/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.voting.votereveal;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcastTimeoutException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.TxMalleabilityException;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.blindvote.BlindVoteConsensus;
import bisq.core.dao.voting.blindvote.BlindVoteService;
import bisq.core.dao.voting.blindvote.BlindVoteValidator;
import bisq.core.dao.voting.blindvote.MyBlindVoteList;
import bisq.core.dao.voting.blindvote.MyBlindVoteListService;
import bisq.core.dao.voting.blindvote.storage.appendonly.BlindVoteAppendOnlyPayload;
import bisq.core.dao.voting.myvote.MyVote;
import bisq.core.dao.voting.myvote.MyVoteListService;

import bisq.network.p2p.P2PService;

import bisq.common.util.Utilities;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.IOException;

import java.util.Optional;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

//TODO case that user misses reveal phase not impl. yet


// TODO We could also broadcast the winning list at the moment the reveal period is over and have the break
// interval as time buffer for all nodes to receive that winning list. All nodes which are in sync with the
// majority data view can broadcast. That way it will become a very unlikely case that a node is missing
// data.

@Slf4j
public class VoteRevealService {
    private final StateService stateService;
    private final MyBlindVoteListService myBlindVoteListService;
    private final BlindVoteService blindVoteService;
    private final BlindVoteValidator blindVoteValidator;
    private final PeriodService periodService;
    private final MyVoteListService myVoteListService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final P2PService p2PService;
    private final WalletsManager walletsManager;

    @Getter
    private final ObservableList<VoteRevealException> voteRevealExceptions = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteRevealService(StateService stateService,
                             MyBlindVoteListService myBlindVoteListService,
                             BlindVoteService blindVoteService,
                             BlindVoteValidator blindVoteValidator,
                             PeriodService periodService,
                             MyVoteListService myVoteListService,
                             BsqWalletService bsqWalletService,
                             BtcWalletService btcWalletService,
                             P2PService p2PService,
                             WalletsManager walletsManager) {
        this.stateService = stateService;
        this.myBlindVoteListService = myBlindVoteListService;
        this.blindVoteService = blindVoteService;
        this.blindVoteValidator = blindVoteValidator;
        this.periodService = periodService;
        this.myVoteListService = myVoteListService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.p2PService = p2PService;
        this.walletsManager = walletsManager;

        voteRevealExceptions.addListener((ListChangeListener<VoteRevealException>) c -> {
            c.next();
            if (c.wasAdded())
                c.getAddedSubList().forEach(exception -> log.error(exception.toString()));
        });

        stateService.addChainHeightListener(this::maybeRevealVotes);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        maybeRevealVotes(stateService.getChainHeight());
    }

    public byte[] getHashOfBlindVoteList() {
        MyBlindVoteList list = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteService, blindVoteValidator);
        return VoteRevealConsensus.getHashOfBlindVoteList(list);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Creation of vote reveal tx is done without user activity!
    // We create automatically the vote reveal tx when we enter the reveal phase of the current cycle when
    // the blind vote was created in case we have not done it already.
    // The voter need to be at least once online in the reveal phase when he has a blind vote created,
    // otherwise his vote becomes invalid and his locked stake will get unlocked
    private void maybeRevealVotes(int chainHeight) {
        if (periodService.getPhaseForHeight(chainHeight) == DaoPhase.Phase.VOTE_REVEAL) {
            myVoteListService.getMyVoteList().stream()
                    .filter(myVote -> myVote.getRevealTxId() == null) // we have not already revealed
                    .filter(myVote -> periodService.isTxInCorrectCycle(myVote.getTxId(), chainHeight))
                    .forEach(myVote -> {
                        // We handle the exception here inside the stream iteration as we have not get triggered from an
                        // outside user intent anyway. We keep errors in a observable list so clients can observe that to
                        // get notified if anything went wrong.
                        try {
                            revealVote(myVote, chainHeight);
                        } catch (IOException | WalletException | TransactionVerificationException
                                | InsufficientMoneyException e) {
                            voteRevealExceptions.add(new VoteRevealException("Exception at calling revealVote.",
                                    e, myVote.getTxId()));
                        } catch (VoteRevealException e) {
                            voteRevealExceptions.add(e);
                        }
                    });
        }
    }

    private void revealVote(MyVote myVote, int chainHeight) throws IOException, WalletException,
            InsufficientMoneyException, TransactionVerificationException, VoteRevealException {
        // We collect all valid blind vote items we received via the p2p network.
        // It might be that different nodes have a different collection of those items.
        // To ensure we get a consensus of the data for later calculating the result we will put a hash of each
        // voters  blind vote collection into the opReturn data and check for a majority at issuance time.
        // The voters "vote" with their stake at the reveal tx for their version of the blind vote collection.
        byte[] hashOfBlindVoteList = getHashOfBlindVoteList();

        log.info("Sha256Ripemd160 hash of hashOfBlindVoteList " + Utilities.bytesAsHexString(hashOfBlindVoteList));
        byte[] opReturnData = VoteRevealConsensus.getOpReturnData(hashOfBlindVoteList, myVote.getSecretKey());

        // We search for my unspent stake output.
        // myVote is already tested if it is in current cycle at maybeRevealVotes
        final Set<TxOutput> blindVoteStakeTxOutputs = stateService.getUnspentBlindVoteStakeTxOutputs();
        // We expect that the blind vote tx and stake output is available. If not we throw an exception.
        TxOutput stakeTxOutput = blindVoteStakeTxOutputs.stream()
                .filter(txOutput -> txOutput.getTxId().equals(myVote.getTxId()))
                .findFirst()
                .orElseThrow(() -> new VoteRevealException("stakeTxOutput is not found for myVote.", myVote));

        // TxOutput has to be in the current cycle. Phase is checked in the parser anyway.
        if (periodService.isTxInCorrectCycle(stakeTxOutput.getTxId(), chainHeight)) {
            Transaction voteRevealTx = getVoteRevealTx(stakeTxOutput, opReturnData);
            log.info("voteRevealTx={}", voteRevealTx);
            walletsManager.publishAndCommitBsqTx(voteRevealTx, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    log.info("voteRevealTx successfully broadcasted.");
                    myVoteListService.applyRevealTxId(myVote, voteRevealTx.getHashAsString());
                }

                @Override
                public void onTimeout(TxBroadcastTimeoutException exception) {
                    log.error(exception.toString());
                    // TODO handle
                    voteRevealExceptions.add(new VoteRevealException("Publishing of voteRevealTx failed.",
                            exception, voteRevealTx));
                }

                @Override
                public void onTxMalleability(TxMalleabilityException exception) {
                    log.error(exception.toString());
                    // TODO handle
                    voteRevealExceptions.add(new VoteRevealException("Publishing of voteRevealTx failed.",
                            exception, voteRevealTx));
                }

                @Override
                public void onFailure(TxBroadcastException exception) {
                    log.error(exception.toString());
                    // TODO handle
                    voteRevealExceptions.add(new VoteRevealException("Publishing of voteRevealTx failed.",
                            exception, voteRevealTx));
                }
            });

            // We publish all our blindVotes to a append-only data store. Even we have no feature to remove a already
            // published blind vote it could have been removed technically and we want to keep that option open in case
            // we support that one day. So we did not want to use the append only data store in the first event when we
            // created the blind votes but used the normal protected data store. Now after the reveal removal of any
            // blind vote would nto make sense and we want to guarantee that the data is immutably persisted.
            // From now on we only access blind vote data from that append only data store.
            final MyBlindVoteList sortedBlindVoteListOfCycle = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteService, blindVoteValidator);
            publishToAppendOnlyDataStore(sortedBlindVoteListOfCycle);
        } else {
            final String msg = "Tx of stake out put is not in our cycle. That must not happen.";
            log.error("{}. chainHeight={},  blindVoteTxId()={}", msg, chainHeight, myVote.getTxId());
            voteRevealExceptions.add(new VoteRevealException(msg,
                    stakeTxOutput.getTxId()));
        }
    }

    private Transaction getVoteRevealTx(TxOutput stakeTxOutput, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedVoteRevealTx(stakeTxOutput);
        Transaction txWithBtcFee = btcWalletService.completePreparedVoteRevealTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }

    private void publishToAppendOnlyDataStore(MyBlindVoteList blindVotes) {
        final Optional<Block> optionalBlock = stateService.getBlockAtHeight(blindVoteService.getTriggerHeight());
        if (optionalBlock.isPresent()) {
            String blockHash = optionalBlock.get().getHash();
            blindVotes.stream()
                    .filter(blindVoteValidator::isValidAndConfirmed)
                    .map(BlindVoteAppendOnlyPayload::new)
                    .forEach(appendOnlyPayload -> {
                        boolean success = p2PService.addPersistableNetworkPayload(appendOnlyPayload, true);
                        if (!success)
                            log.warn("publishToAppendOnlyDataStore failed for blindVote " + appendOnlyPayload.getBlindVote());
                    });
        }
    }
}

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

package bisq.core.dao.vote.votereveal;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcastTimeoutException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.TxMalleabilityException;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.Block;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.blindvote.BlindVote;
import bisq.core.dao.vote.blindvote.BlindVoteConsensus;
import bisq.core.dao.vote.blindvote.BlindVoteList;
import bisq.core.dao.vote.blindvote.BlindVoteService;
import bisq.core.dao.vote.myvote.MyVote;
import bisq.core.dao.vote.myvote.MyVoteService;

import bisq.common.util.Utilities;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.IOException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

//TODO case that user misses reveal phase not impl. yet

@Slf4j
public class VoteRevealService implements StateService.BlockListener {
    private final StateService stateService;
    private final MyVoteService myVoteService;
    private final PeriodService periodService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final WalletsManager walletsManager;
    private final BlindVoteService blindVoteService;

    @Getter
    private final ObservableList<VoteRevealException> voteRevealExceptions = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteRevealService(StateService stateService,
                             MyVoteService myVoteService,
                             PeriodService periodService,
                             BsqWalletService bsqWalletService,
                             BtcWalletService btcWalletService,
                             WalletsManager walletsManager,
                             BlindVoteService blindVoteService) {
        this.stateService = stateService;
        this.myVoteService = myVoteService;
        this.periodService = periodService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.walletsManager = walletsManager;

        this.blindVoteService = blindVoteService;

        voteRevealExceptions.addListener((ListChangeListener<VoteRevealException>) c -> {
            c.next();
            if (c.wasAdded())
                c.getAddedSubList().forEach(exception -> log.error(exception.toString()));
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        stateService.addBlockListener(this);
    }

    @SuppressWarnings("EmptyMethod")
    public void shutDown() {
        // TODO keep for later, maybe we get resources to clean up later
    }

    @Override
    public void onBlockAdded(Block block) {
        if (periodService.getPhaseForHeight(block.getHeight()) == PeriodService.Phase.VOTE_REVEAL) {
            // A phase change is triggered by a new block but we need to wait for the parser to complete
            //TODO use handler only triggered at end of parsing. -> Refactor bsqBlockChain and BsqNode handlers
            maybeRevealVotes(block.getHeight());
        }
    }

    public BlindVoteList getSortedBlindVoteListOfCycle(int chainHeight) {
        final List<BlindVote> list = getBlindVoteListOfCycle(chainHeight);
        if (list.isEmpty())
            log.warn("sortBlindVoteList is empty");
        BlindVoteConsensus.sortBlindVoteList(list);
        log.info("getSortedBlindVoteListForCurrentCycle list={}", list);
        return new BlindVoteList(list);
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
        myVoteService.getMyVoteList().stream()
                .filter(myVote -> myVote.getRevealTxId() == null) // we have not already revealed
                .filter(myVote -> periodService.isTxInPhase(myVote.getTxId(), PeriodService.Phase.BLIND_VOTE))
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

    private void revealVote(MyVote myVote, int chainHeight) throws IOException, WalletException,
            InsufficientMoneyException, TransactionVerificationException, VoteRevealException {
        // We collect all valid blind vote items we received via the p2p network.
        // It might be that different nodes have a different collection of those items.
        // To ensure we get a consensus of the data for later calculating the result we will put a hash of each
        // voters  blind vote collection into the opReturn data and check for a majority at issuance time.
        // The voters "vote" with their stake at the reveal tx for their version of the blind vote collection.
        final BlindVoteList blindVoteList = getSortedBlindVoteListOfCycle(chainHeight);
        byte[] hashOfBlindVoteList = VoteRevealConsensus.getHashOfBlindVoteList(blindVoteList);

        log.info("Sha256Ripemd160 hash of hashOfBlindVoteList " + Utilities.bytesAsHexString(hashOfBlindVoteList));
        byte[] opReturnData = VoteRevealConsensus.getOpReturnData(hashOfBlindVoteList, myVote.getSecretKey());

        // We search for my unspent stake output.
        // myVote is already tested if it is in current cycle at maybeRevealVotes
        final Set<TxOutput> blindVoteStakeTxOutputs = stateService.getUnspentBlindVoteStakeTxOutputs();
        // We expect that the blind vote tx and stake output is available. If not we throw an exception.
        TxOutput stakeTxOutput = blindVoteStakeTxOutputs.stream()
                .filter(txOutput -> txOutput.getTxId().equals(myVote.getTxId())).findFirst()
                .orElseThrow(() -> new VoteRevealException("stakeTxOutput is not found for myVote.", myVote));

        // TxOutput has to be in the current cycle. Phase is checked in the parser anyway.
        if (periodService.isTxInCorrectCycle(stakeTxOutput.getTxId(), chainHeight)) {
            Transaction voteRevealTx = getVoteRevealTx(stakeTxOutput, opReturnData);
            log.info("voteRevealTx={}", voteRevealTx);
            walletsManager.publishAndCommitBsqTx(voteRevealTx, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    log.info("voteRevealTx successfully broadcasted.");
                    myVoteService.applyRevealTxId(myVote, voteRevealTx.getHashAsString());
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
        } else {
            final String msg = "Tx of stake out put is not in our cycle. That must not happen.";
            log.error("{}. chainHeight={},  blindVoteTxId()={}", msg, chainHeight, myVote.getTxId());
            voteRevealExceptions.add(new VoteRevealException(msg,
                    stakeTxOutput.getTxId()));
        }
    }

    private List<BlindVote> getBlindVoteListOfCycle(int chainHeight) {
        if (blindVoteService.getOpenBlindVoteList().isEmpty())
            log.warn("blindVoteService.getObservableList() is empty");
        return blindVoteService.getOpenBlindVoteList().stream()
                .filter(blindVote -> {
                    final boolean txInPhase = periodService.isTxInPhase(blindVote.getTxId(), PeriodService.Phase.BLIND_VOTE);
                    if (!txInPhase)
                        log.warn("Blind vote tx is not in correct phase txId=", blindVote.getTxId());
                    return txInPhase;
                })
                .filter(blindVote -> periodService.isTxInCorrectCycle(blindVote.getTxId(), chainHeight))
                .collect(Collectors.toList());
    }

    private Transaction getVoteRevealTx(TxOutput stakeTxOutput, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedVoteRevealTx(stakeTxOutput);
        Transaction txWithBtcFee = btcWalletService.completePreparedVoteRevealTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }
}

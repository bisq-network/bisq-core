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
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.TxOutput;
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
public class VoteRevealService implements BsqBlockChain.Listener {
    private final ReadableBsqBlockChain readableBsqBlockChain;
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
    public VoteRevealService(ReadableBsqBlockChain readableBsqBlockChain,
                             MyVoteService myVoteService,
                             PeriodService periodService,
                             BsqWalletService bsqWalletService,
                             BtcWalletService btcWalletService,
                             WalletsManager walletsManager,
                             BlindVoteService blindVoteService) {
        this.readableBsqBlockChain = readableBsqBlockChain;
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
        readableBsqBlockChain.addListener(this);
    }

    @SuppressWarnings("EmptyMethod")
    public void shutDown() {
        // TODO keep for later, maybe we get resources to clean up later
    }

    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        if (periodService.getPhaseForHeight(bsqBlock.getHeight()) == PeriodService.Phase.VOTE_REVEAL) {
            // A phase change is triggered by a new block but we need to wait for the parser to complete
            //TODO use handler only triggered at end of parsing. -> Refactor bsqBlockChain and BsqNode handlers
            log.info("blockHeight " + bsqBlock.getHeight());
            maybeRevealVotes();
        }
    }

    public BlindVoteList getSortedBlindVoteListForCurrentCycle() {
        final List<BlindVote> list = getBlindVoteListForCurrentCycle();
        BlindVoteConsensus.sortedBlindVoteList(list);
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
    private void maybeRevealVotes() {
        myVoteService.getMyVoteList().stream()
                .filter(myVote -> myVote.getRevealTxId() == null)
                .filter(myVote -> periodService.isTxInCurrentCycle(myVote.getTxId()))
                .forEach(myVote -> {
                    // We handle the exception here inside the stream iteration as we have not get triggered from an
                    // outside user intent anyway. We keep errors in a observable list so clients can observe that to
                    // get notified if anything went wrong.
                    try {
                        revealVote(myVote);
                    } catch (IOException | WalletException | TransactionVerificationException
                            | InsufficientMoneyException e) {
                        voteRevealExceptions.add(new VoteRevealException("Exception at calling revealVote.",
                                e, myVote.getTxId()));
                    } catch (VoteRevealException e) {
                        voteRevealExceptions.add(e);
                    }
                });
    }

    private void revealVote(MyVote myVote) throws IOException, WalletException, InsufficientMoneyException,
            TransactionVerificationException, VoteRevealException {
        final BlindVoteList blindVoteList = getSortedBlindVoteListForCurrentCycle();
        byte[] hashOfBlindVoteList = VoteRevealConsensus.getHashOfBlindVoteList(blindVoteList);
        log.info("Sha256Ripemd160 hash of hashOfBlindVoteList " + Utilities.bytesAsHexString(hashOfBlindVoteList));
        byte[] opReturnData = VoteRevealConsensus.getOpReturnData(hashOfBlindVoteList, myVote.getSecretKey());

        // We search for my unspent stake output.
        // myVote is already tested if it is in current cycle at maybeRevealVotes
        final Set<TxOutput> blindVoteStakeTxOutputs = readableBsqBlockChain.getBlindVoteStakeTxOutputs();

        // We expect that the blind vote tx and stake output is available. If not we throw an exception.
        TxOutput stakeTxOutput = blindVoteStakeTxOutputs.stream()
                .filter(txOutput -> txOutput.getTxId().equals(myVote.getTxId())).findFirst()
                .orElseThrow(() -> new VoteRevealException("stakeTxOutput is not found for myVote.", myVote));
        Transaction voteRevealTx = getVoteRevealTx(stakeTxOutput, opReturnData);

        //TODO not sure if it is better to apply the state changes only in the success handler at publishing the
        // tx or if we do it before we know if publishing was successful.
        // Tx broadcast can be unreliable as it was when Tor was Dos'ed.
        // The tx will get republished automatically at restart but we would not get called our handler in such
        // a case which would lead to an inconsistent state.
        // We get logged a waring in the Broadcaster.broadcastTx method if we don't get the tx broadcasted in
        // 8 seconds.
        //TODO add timeout error to broadcaster API
        myVoteService.applyRevealTxId(myVote, voteRevealTx.getHashAsString());
        // TODO not updated to TxBroadcaster changes because not in sync with voting branch anyway
      /*  walletsManager.publishAndCommitBsqTx(voteRevealTx, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
                log.info("Reveal vote tx successfully broadcasted. txID={}", voteRevealTx.getHashAsString());
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                voteRevealExceptions.add(new VoteRevealException("Publishing of voteRevealTx failed.", t, voteRevealTx));
            }
        });*/
    }

    private List<BlindVote> getBlindVoteListForCurrentCycle() {
        return blindVoteService.getBlindVoteList().stream()
                .filter(blindVote -> periodService.isTxInCurrentCycle(blindVote.getTxId()))
                .collect(Collectors.toList());
    }

    private Transaction getVoteRevealTx(TxOutput stakeTxOutput, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedVoteRevealTx(stakeTxOutput);
        Transaction txWithBtcFee = btcWalletService.completePreparedVoteRevealTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }
}

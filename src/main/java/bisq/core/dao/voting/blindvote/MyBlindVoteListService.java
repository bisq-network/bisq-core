/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.voting.blindvote;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcastTimeoutException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.TxMalleabilityException;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.ParseBlockChainListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ballot.BallotList;
import bisq.core.dao.voting.ballot.BallotListService;
import bisq.core.dao.voting.blindvote.storage.appendonly.BlindVoteAppendOnlyPayload;
import bisq.core.dao.voting.myvote.MyVoteListService;
import bisq.core.dao.voting.proposal.ProposalValidator;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;
import bisq.common.crypto.CryptoException;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ExceptionHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;

import javax.crypto.SecretKey;

import java.io.IOException;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Publishes blind vote tx and blind vote payload to p2p network.
 * Maintains MyBlindVoteList for own blind votes. Triggers republishing of my blind votes at startup during blind
 * vote phase of current cycle.
 */
@Slf4j
public class MyBlindVoteListService implements PersistedDataHost, ParseBlockChainListener {
    private final P2PService p2PService;
    private final StateService stateService;
    private final PeriodService periodService;
    private final WalletsManager walletsManager;
    private final Storage<MyBlindVoteList> storage;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final BallotListService ballotListService;
    private final MyVoteListService myVoteListService;
    private final ProposalValidator proposalValidator;
    private ChangeListener<Number> numConnectedPeersListener;
    @Getter
    private final MyBlindVoteList myBlindVoteList = new MyBlindVoteList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyBlindVoteListService(P2PService p2PService,
                                  StateService stateService,
                                  PeriodService periodService,
                                  WalletsManager walletsManager,
                                  Storage<MyBlindVoteList> storage,
                                  BsqWalletService bsqWalletService,
                                  BtcWalletService btcWalletService,
                                  BallotListService ballotListService,
                                  MyVoteListService myVoteListService,
                                  ProposalValidator proposalValidator) {
        this.p2PService = p2PService;
        this.stateService = stateService;
        this.periodService = periodService;
        this.walletsManager = walletsManager;
        this.storage = storage;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.ballotListService = ballotListService;
        this.myVoteListService = myVoteListService;
        this.proposalValidator = proposalValidator;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            MyBlindVoteList persisted = storage.initAndGetPersisted(myBlindVoteList, 100);
            if (persisted != null) {
                myBlindVoteList.clear();
                myBlindVoteList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ParseBlockChainListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
        rePublishOnceWellConnected();
        numConnectedPeersListener = (observable, oldValue, newValue) -> rePublishOnceWellConnected();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
    }

    public Coin getBlindVoteFee() {
        return BlindVoteConsensus.getFee(stateService, stateService.getChainHeight());
    }

    // For showing fee estimation in confirmation popup
    public Transaction getDummyBlindVoteTx(Coin stake, Coin fee)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        // We set dummy opReturn data
        return getBlindVoteTx(stake, fee, new byte[22]);
    }

    public void publishBlindVote(Coin stake, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        try {
            BallotList sortedBallotList = BlindVoteConsensus.getSortedBallotList(ballotListService, proposalValidator);
            final VoteWithProposalTxIdList voteWithProposalTxIdList = getSortedVoteWithProposalTxIdList(sortedBallotList);
            log.info("voteWithProposalTxIdList used in blind vote. voteWithProposalTxIdList={}", voteWithProposalTxIdList);

            SecretKey secretKey = BlindVoteConsensus.getSecretKey();
            byte[] encryptedVotes = BlindVoteConsensus.getEncryptedVotes(voteWithProposalTxIdList, secretKey);

            final byte[] hash = BlindVoteConsensus.getHashOfEncryptedProposalList(encryptedVotes);
            log.info("Sha256Ripemd160 hash of encryptedVotes: " + Utilities.bytesAsHexString(hash));
            byte[] opReturnData = BlindVoteConsensus.getOpReturnData(hash);

            final Transaction blindVoteTx = getBlindVoteTx(stake, getBlindVoteFee(), opReturnData);
            log.info("blindVoteTx={}", blindVoteTx);
            walletsManager.publishAndCommitBsqTx(blindVoteTx, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    resultHandler.handleResult();
                }

                @Override
                public void onTimeout(TxBroadcastTimeoutException exception) {
                    // TODO handle
                    // We need to handle cases where a timeout happens and
                    // the tx might get broadcasted at a later restart!
                    // We need to be sure that in case of a failed tx the locked stake gets unlocked!
                    exceptionHandler.handleException(exception);
                }

                @Override
                public void onTxMalleability(TxMalleabilityException exception) {
                    // TODO handle
                    // We need to be sure that in case of a failed tx the locked stake gets unlocked!
                    exceptionHandler.handleException(exception);
                }

                @Override
                public void onFailure(TxBroadcastException exception) {
                    // TODO handle
                    // We need to be sure that in case of a failed tx the locked stake gets unlocked!
                    exceptionHandler.handleException(exception);
                }
            });

            // We prefer to not wait for the tx broadcast as if the tx broadcast would fail we still prefer to have our
            // blind vote stored and broadcasted to the p2p network. The tx might get re-broadcasted at a restart and
            // in worst case if it does not succeed the blind vote will be ignored anyway.
            // Inconsistently propagated blind votes in the p2p network could have potentially worse effects.
            BlindVote blindVote = new BlindVote(encryptedVotes, blindVoteTx.getHashAsString(), stake.value);
            addToList(blindVote);

            addToP2PNetwork(blindVote, errorMessage -> {
                log.error(errorMessage);
                //TODO define specific exception
                exceptionHandler.handleException(new Exception(errorMessage));
            });

            // We store our source data for the blind vote in myVoteList
            myVoteListService.createAndAddMyVote(sortedBallotList, secretKey, blindVote);
        } catch (CryptoException | TransactionVerificationException | InsufficientMoneyException |
                WalletException | IOException exception) {
            exceptionHandler.handleException(exception);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Transaction getBlindVoteTx(Coin stake, Coin fee, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedBlindVoteTx(fee, stake);
        Transaction txWithBtcFee = btcWalletService.completePreparedBlindVoteTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }

    private VoteWithProposalTxIdList getSortedVoteWithProposalTxIdList(BallotList sortedBallotList) {
        final List<VoteWithProposalTxId> list = sortedBallotList.stream()
                .map(ballot -> new VoteWithProposalTxId(ballot.getProposalTxId(), ballot.getVote()))
                .collect(Collectors.toList());
        return new VoteWithProposalTxIdList(list);
    }

    private void rePublishOnceWellConnected() {
        if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
            p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);

            myBlindVoteList.forEach(blindVote -> {
                final String txId = blindVote.getTxId();
                if (periodService.isTxInPhase(txId, DaoPhase.Phase.BLIND_VOTE) &&
                        periodService.isTxInCorrectCycle(txId, periodService.getChainHeight())) {
                    addToP2PNetwork(blindVote, null);
                }
            });
        }
    }

    private void addToP2PNetwork(BlindVote blindVote, @Nullable ErrorMessageHandler errorMessageHandler) {
        BlindVoteAppendOnlyPayload appendOnlyPayload = new BlindVoteAppendOnlyPayload(blindVote);
        boolean success = p2PService.addPersistableNetworkPayload(appendOnlyPayload, true);

        if (success) {
            log.debug("We added a blindVote to the P2P network. blindVote=" + blindVote);
        } else {
            final String msg = "Adding of blindVote to P2P network failed. blindVote=" + blindVote;
            log.error(msg);
            if (errorMessageHandler != null)
                errorMessageHandler.handleErrorMessage(msg);
        }
    }

    private void addToList(BlindVote blindVote) {
        if (!BlindVoteUtils.containsBlindVote(blindVote, myBlindVoteList.getList())) {
            myBlindVoteList.add(blindVote);
            persist();
        }
    }

    private void persist() {
        storage.queueUpForSave();
    }
}

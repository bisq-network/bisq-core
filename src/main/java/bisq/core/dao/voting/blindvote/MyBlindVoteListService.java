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
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.BallotList;
import bisq.core.dao.voting.ballot.BallotListService;
import bisq.core.dao.voting.blindvote.storage.protectedstorage.BlindVotePayload;
import bisq.core.dao.voting.myvote.MyVoteListService;
import bisq.core.dao.voting.proposal.ProposalValidator;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
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

import java.security.PublicKey;

import java.io.IOException;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes blind vote tx and blind vote payload to p2p network.
 * Maintains MyBlindVoteList for own blind votes. Triggers republishing of my blind votes at startup.
 */
@Slf4j
public class MyBlindVoteListService implements PersistedDataHost {
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
    private final ChangeListener<Number> numConnectedPeersListener;
    @Getter
    private final MyBlindVoteList myMyBlindVoteList = new MyBlindVoteList();
    private final PublicKey signaturePubKey;


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
                                  ProposalValidator proposalValidator,
                                  KeyRing keyRing) {
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
        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();

        numConnectedPeersListener = (observable, oldValue, newValue) -> maybeRePublish();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            MyBlindVoteList persisted = storage.initAndGetPersisted(myMyBlindVoteList, 100);
            if (persisted != null) {
                myMyBlindVoteList.clear();
                myMyBlindVoteList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        maybeRePublish();
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
            BallotList sortedBallotList = getSortedBallotList();
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

            // We store our source data for the blind vote in myVoteList
            myVoteListService.createAndAddMyVote(sortedBallotList, secretKey, blindVote);

            addToP2PNetwork(blindVote, errorMessage -> {
                log.error(errorMessage);
                //TODO define specific exception
                exceptionHandler.handleException(new Exception(errorMessage));
            });
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

    private BallotList getSortedBallotList() {
        final List<Ballot> list = ballotListService.getBallotList().stream()
                .filter(ballot -> proposalValidator.isValidAndConfirmed(ballot.getProposal()))
                .collect(Collectors.toList());
        BlindVoteConsensus.sortBallotList(list);
        return new BallotList(list);
    }

    private void maybeRePublish() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false. Also better for production version
        // to avoid activity peaks at startup
        UserThread.runAfter(() -> {
            if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
                p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
                rePublish();
            }
        }, 2);
    }

    private void rePublish() {
        myMyBlindVoteList.forEach(blindVote -> {
            final String txId = blindVote.getTxId();
            if (periodService.isTxInPhase(txId, DaoPhase.Phase.BLIND_VOTE) &&
                    periodService.isTxInCorrectCycle(txId, periodService.getChainHeight())) {
                if (!addToP2PNetwork(blindVote))
                    log.warn("Adding of blindVote to P2P network failed.\nblindVote=" + blindVote);
            }
        });
    }

    private void addToP2PNetwork(BlindVote blindVote, ErrorMessageHandler errorMessageHandler) {
        final boolean success = addToP2PNetwork(blindVote);
        if (success) {
            log.debug("We added a blindVote to the P2P network. blindVote=" + blindVote);
        } else {
            final String msg = "Adding of blindVote to P2P network failed. blindVote=" + blindVote;
            log.error(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
    }

    private void addToList(BlindVote blindVote) {
        if (!BlindVoteUtils.containsBlindVote(blindVote, myMyBlindVoteList.getList())) {
            myMyBlindVoteList.add(blindVote);
            persist();
        }
    }

    private boolean addToP2PNetwork(BlindVote blindVote) {
        return p2PService.addProtectedStorageEntry(new BlindVotePayload(blindVote, signaturePubKey), true);
    }

    private void persist() {
        storage.queueUpForSave();
    }
}

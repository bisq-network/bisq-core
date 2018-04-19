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

package bisq.core.dao.consensus.vote.myvote;

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
import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.events.payloads.BlindVote;
import bisq.core.dao.consensus.state.events.payloads.Proposal;
import bisq.core.dao.consensus.vote.blindvote.BlindVoteConsensus;
import bisq.core.dao.consensus.vote.proposal.Ballot;
import bisq.core.dao.consensus.vote.proposal.BallotList;
import bisq.core.dao.consensus.vote.proposal.ProposalFactory;
import bisq.core.dao.consensus.vote.proposal.ProposalService;
import bisq.core.dao.consensus.vote.proposal.param.ChangeParamService;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
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

import lombok.extern.slf4j.Slf4j;

/**
 * Creates and published blind vote and blind vote tx. After broadcast it creates myVote which gets persisted and holds
 * all proposals with the votes.
 * Republished all my active myVotes at startup and applies the revealTxId to myVote once the reveal tx is published.
 * <p>
 * Executed from the user tread.
 */
@Slf4j
public class MyVoteService implements PersistedDataHost {
    private final PeriodService periodService;
    private final ProposalService proposalService;
    private final StateService stateService;
    private final P2PService p2PService;
    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final ChangeParamService changeParamService;
    private final Storage<MyVoteList> storage;
    private final PublicKey signaturePubKey;

    // MyVoteList is wrapper for persistence. From outside we access only list inside of wrapper.
    private final MyVoteList myVoteList = new MyVoteList();

    private ChangeListener<Number> numConnectedPeersListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyVoteService(PeriodService periodService,
                         ProposalService proposalService,
                         StateService stateService,
                         P2PService p2PService,
                         WalletsManager walletsManager,
                         BsqWalletService bsqWalletService,
                         BtcWalletService btcWalletService,
                         ChangeParamService changeParamService,
                         KeyRing keyRing,
                         Storage<MyVoteList> storage) {
        this.periodService = periodService;
        this.proposalService = proposalService;
        this.stateService = stateService;
        this.p2PService = p2PService;
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.changeParamService = changeParamService;
        this.storage = storage;
        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            MyVoteList persisted = storage.initAndGetPersisted(myVoteList, 20);
            if (persisted != null) {
                this.myVoteList.clear();
                this.myVoteList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        // Republish own active blindVotes once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> publishMyBlindVotesIfWellConnected();
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
        publishMyBlindVotesIfWellConnected();
    }

    // Useful for showing fee estimation in confirmation popup so we expose it publicly
    public Transaction getBlindVoteTx(Coin stake, Coin fee, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedBlindVoteTx(fee, stake);
        Transaction txWithBtcFee = btcWalletService.completePreparedBlindVoteTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }

    public void publishBlindVote(Coin stake, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        try {
            BallotList ballotList = getSortedProposalList();
            log.info("BallotList used in blind vote. ballotList={}", ballotList);

            SecretKey secretKey = BlindVoteConsensus.getSecretKey();
            byte[] encryptedProposalList = BlindVoteConsensus.getEncryptedProposalList(ballotList, secretKey);

            final byte[] hash = BlindVoteConsensus.getHashOfEncryptedProposalList(encryptedProposalList);
            log.info("Sha256Ripemd160 hash of encryptedProposalList: " + Utilities.bytesAsHexString(hash));
            byte[] opReturnData = BlindVoteConsensus.getOpReturnData(hash);
            final Coin fee = BlindVoteConsensus.getFee(changeParamService, stateService.getChainHeight());
            final Transaction blindVoteTx = getBlindVoteTx(stake, fee, opReturnData);
            log.info("blindVoteTx={}", blindVoteTx);
            walletsManager.publishAndCommitBsqTx(blindVoteTx, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    onTxBroadcasted(encryptedProposalList, blindVoteTx, stake, resultHandler, exceptionHandler,
                            ballotList, secretKey);
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
        } catch (CryptoException | TransactionVerificationException | InsufficientMoneyException |
                WalletException | IOException exception) {
            exceptionHandler.handleException(exception);
        }
    }

    public void applyRevealTxId(MyVote myVote, String voteRevealTxId) {
        myVote.setRevealTxId(voteRevealTxId);
        log.info("Applied revealTxId to myVote.\nmyVote={}\nvoteRevealTxId={}", myVote, voteRevealTxId);
        persist();
    }

    public List<MyVote> getMyVoteList() {
        return myVoteList.getList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BallotList getSortedProposalList() {
        List<Proposal> proposalPayloadsFromStateService = stateService.getProposalPayloads().stream()
                .map(ProposalFactory::getProposalFromPayload)
                .filter(proposal -> periodService.isTxInCorrectCycle(proposal.getTxId(), stateService.getChainHeight()))
                .map(Ballot::getProposal)
                .collect(Collectors.toList());

        List<Ballot> ballots = proposalService.getOpenBallotList().stream()
                .filter(proposal -> periodService.isTxInCorrectCycle(proposal.getTxId(), stateService.getChainHeight()))
                .filter(proposal -> proposalPayloadsFromStateService.contains(proposal.getProposal()))
                .collect(Collectors.toList());

        BlindVoteConsensus.sortProposalList(ballots);
        return new BallotList(ballots);
    }

    private void onTxBroadcasted(byte[] encryptedProposalList, Transaction blindVoteTx, Coin stake,
                                 ResultHandler resultHandler, ExceptionHandler exceptionHandler,
                                 BallotList ballotList, SecretKey secretKey) {
        BlindVote blindVote = new BlindVote(encryptedProposalList, blindVoteTx.getHashAsString(),
                stake.value, signaturePubKey);

        // We map from user thread to parser thread as we will read the block height in the addBlindVoteToList method
        // and want to avoid inconsistency from threading issues.
        // nodeExecutor.get().execute(() -> addBlindVoteToList(blindVote, true));

        if (p2PService.addProtectedStorageEntry(blindVote, true)) {
            log.info("Added blindVote to P2P network.\nblindVote={}", blindVote);
            resultHandler.handleResult();
        } else {
            final String msg = "Adding of blindVote to P2P network failed.\nblindVote=" + blindVote;
            log.error(msg);
            //TODO define specific exception
            exceptionHandler.handleException(new Exception(msg));
        }

        MyVote myVote = new MyVote(ballotList, Encryption.getSecretKeyBytes(secretKey), blindVote);
        log.info("Add new MyVote to myVotesList list.\nMyVote=" + myVote);
        myVoteList.add(myVote);
        persist();
    }


    private void publishMyBlindVotesIfWellConnected() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false. Also better for production version
        // to avoid activity peaks at startup
        UserThread.runAfter(() -> {
            if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
                p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
                publishMyBlindVotes();
            }
        }, 2);
    }

    private void publishMyBlindVotes() {
        getMyVoteList().stream()
                .filter(myVote -> periodService.isTxInPhase(myVote.getTxId(), Phase.BLIND_VOTE))
                .filter(myVote -> periodService.isTxInCorrectCycle(myVote.getTxId(), stateService.getChainHeight()))
                .forEach(myVote -> {
                    if (myVote.getRevealTxId() == null) {
                        if (addBlindVoteToP2PNetwork(myVote.getBlindVote())) {
                            log.info("Added BlindVote to P2P network.\nBlindVote={}", myVote.getBlindVote());
                        } else {
                            log.warn("Adding of BlindVote to P2P network failed.\nBlindVote={}", myVote.getBlindVote());
                        }
                    } else {
                        final String msg = "revealTxId must be null at publishMyBlindVotes.\nmyVote=" + myVote;
                        DevEnv.logErrorAndThrowIfDevMode(msg);
                    }
                });
    }

    private boolean addBlindVoteToP2PNetwork(BlindVote blindVote) {
        return p2PService.addProtectedStorageEntry(blindVote, true);
    }

    private void persist() {
        storage.queueUpForSave();
    }
}

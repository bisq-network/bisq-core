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

package bisq.core.dao.vote.blindvote;

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
import bisq.core.dao.param.DaoParamService;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.vo.BsqBlock;
import bisq.core.dao.state.blockchain.vo.Tx;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.myvote.MyVoteService;
import bisq.core.dao.vote.proposal.Proposal;
import bisq.core.dao.vote.proposal.ProposalList;
import bisq.core.dao.vote.proposal.ProposalPayload;
import bisq.core.dao.vote.proposal.ProposalPayloadValidator;
import bisq.core.dao.vote.proposal.ProposalService;
import bisq.core.dao.vote.proposal.ValidationException;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ExceptionHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import javax.crypto.SecretKey;

import java.security.PublicKey;

import java.io.IOException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and published blind vote objects and tx as well as maintains blindVoteList.
 *
 * TODO split up
 */
@Slf4j
public class BlindVoteService implements PersistedDataHost, HashMapChangedListener, StateService.Listener {
    private final P2PService p2PService;
    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final PeriodService periodService;
    private final StateService stateService;
    private final PublicKey signaturePubKey;
    private final MyVoteService myVoteService;
    private final ProposalService proposalService;
    private final DaoParamService daoParamService;
    private final ProposalPayloadValidator proposalPayloadValidator;
    private final BlindVoteValidator blindVoteValidator;
    private final BtcWalletService btcWalletService;
    private final Storage<BlindVoteList> storage;
    private ChangeListener<Number> numConnectedPeersListener;


    @Getter
    private final ObservableList<BlindVote> observableList = FXCollections.observableArrayList();
    private final BlindVoteList blindVoteList = new BlindVoteList(observableList);
    @Getter
    private final FilteredList<BlindVote> validOrMyUnconfirmedBlindVotes = new FilteredList<>(observableList);
    @Getter
    private final FilteredList<BlindVote> validBlindVotes = new FilteredList<>(observableList);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteService(P2PService p2PService,
                            WalletsManager walletsManager,
                            BsqWalletService bsqWalletService,
                            PeriodService periodService,
                            StateService stateService,
                            MyVoteService myVoteService,
                            ProposalService proposalService,
                            DaoParamService daoParamService,
                            ProposalPayloadValidator proposalPayloadValidator,
                            BlindVoteValidator blindVoteValidator,
                            BtcWalletService btcWalletService,
                            Storage<BlindVoteList> storage,
                            KeyRing keyRing) {
        this.p2PService = p2PService;
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.periodService = periodService;
        this.stateService = stateService;
        this.myVoteService = myVoteService;
        this.proposalService = proposalService;
        this.daoParamService = daoParamService;
        this.proposalPayloadValidator = proposalPayloadValidator;
        this.blindVoteValidator = blindVoteValidator;
        this.btcWalletService = btcWalletService;
        this.storage = storage;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().getMap().values()
                .forEach(entry -> onProtectedStorageEntry(entry, false));

        // Republish own active proposals once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> rePublishWhenWellConnected();
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
        rePublishWhenWellConnected();

        stateService.addListener(this);
    }

    public void shutDown() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            BlindVoteList persisted = storage.initAndGetPersisted(blindVoteList, 20);
            if (persisted != null) {
                this.observableList.clear();
                this.observableList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get called delayed as we map to user thread! If follow up methods requests the blockchain data it
    // might be out of sync!
    // TODO find a solution to fix that
    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        onBlockHeightChanged(bsqBlock.getHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onAdded(ProtectedStorageEntry entry) {
        onProtectedStorageEntry(entry, true);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry privateStorageEntry) {
        if (privateStorageEntry.getProtectedStoragePayload() instanceof BlindVote)
            throw new UnsupportedOperationException("Removal of blind vote data is not supported");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Useful for showing fee estimation in confirmation popup so we expose it publicly
    public Transaction getBlindVoteTx(Coin stake, Coin fee, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedBlindVoteTx(fee, stake);
        Transaction txWithBtcFee = btcWalletService.completePreparedBlindVoteTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }

    public void publishBlindVote(Coin stake, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        try {
            ProposalList proposalList = getSortedProposalList(stateService.getChainHeadHeight());
            log.info("ProposalList used in blind vote. proposalList={}", proposalList);

            //TODO publish to P2P network for more redundancy?

            SecretKey secretKey = BlindVoteConsensus.getSecretKey();
            byte[] encryptedProposalList = BlindVoteConsensus.getEncryptedProposalList(proposalList, secretKey);

            final byte[] hash = BlindVoteConsensus.getHashOfEncryptedProposalList(encryptedProposalList);
            log.info("Sha256Ripemd160 hash of encryptedProposalList: " + Utilities.bytesAsHexString(hash));
            byte[] opReturnData = BlindVoteConsensus.getOpReturnData(hash);
            final Coin fee = BlindVoteConsensus.getFee(daoParamService, stateService.getChainHeadHeight());
            final Transaction blindVoteTx = getBlindVoteTx(stake, fee, opReturnData);
            log.info("blindVoteTx={}", blindVoteTx);
            walletsManager.publishAndCommitBsqTx(blindVoteTx, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    onTxBroadcasted(encryptedProposalList, blindVoteTx, stake, resultHandler, exceptionHandler,
                            proposalList, secretKey);
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

    private void onTxBroadcasted(byte[] encryptedProposalList, Transaction blindVoteTx, Coin stake,
                                 ResultHandler resultHandler, ExceptionHandler exceptionHandler,
                                 ProposalList proposalList, SecretKey secretKey) {
        BlindVote blindVote = new BlindVote(encryptedProposalList, blindVoteTx.getHashAsString(),
                stake.value, signaturePubKey);
        addBlindVoteToList(blindVote, true);

        if (addToP2PNetwork(blindVote)) {
            log.info("Added blindVote to P2P network.\nblindVote={}", blindVote);
            resultHandler.handleResult();
        } else {
            final String msg = "Adding of blindVote to P2P network failed.\nblindVote=" + blindVote;
            log.error(msg);
            //TODO define specific exception
            exceptionHandler.handleException(new Exception(msg));
        }

        myVoteService.applyNewBlindVote(proposalList, secretKey, blindVote);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onProtectedStorageEntry(ProtectedStorageEntry privateStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload privateStoragePayload = privateStorageEntry.getProtectedStoragePayload();
        if (privateStoragePayload instanceof BlindVote)
            addBlindVoteToList((BlindVote) privateStoragePayload, storeLocally);
    }

    private void onBlockHeightChanged(int height) {
        // We display own unconfirmed blindVotes as there is no risk. Other blindVotes are only shown after they are in
        //the blockchain and verified
        validOrMyUnconfirmedBlindVotes.setPredicate(blindVote -> (isUnconfirmed(blindVote.getTxId()) &&
                isMine(blindVote)) || isBlindVoteValid(blindVote));
        validBlindVotes.setPredicate(this::isBlindVoteValid);
    }

    private List<ProtectedStoragePayload> getListForRepublishing() {
        return validOrMyUnconfirmedBlindVotes.stream()
                .filter(this::isMine)
                .collect(Collectors.toList());
    }

    private void addBlindVoteToList(BlindVote blindVote, boolean storeLocally) {
        if (!observableList.contains(blindVote)) {
            log.info("We received a BlindVote from the P2P network. BlindVote=" + blindVote);
            observableList.add(blindVote);

            if (storeLocally)
                persist();

            onBlockHeightChanged(stateService.getChainHeadHeight());
        } else {
            if (storeLocally && !isMine(blindVote))
                log.debug("We have that blindVote already in our list. blindVote={}", blindVote);
        }
    }

    private ProposalList getSortedProposalList(int chainHeight) {
        List<Proposal> proposals = proposalService.getProposals().stream()
                .filter(proposal -> isProposalPayloadValid(proposal.getProposalPayload()))
                .filter(proposal -> periodService.isTxInCorrectCycle(proposal.getTxId(), chainHeight))
                .collect(Collectors.toList());
        BlindVoteConsensus.sortProposalList(proposals);
        return new ProposalList(proposals);
    }

    private boolean hasEnoughBsqFunds(Coin stake, Coin fee) {
        return bsqWalletService.getAvailableBalance().compareTo(stake.add(fee)) >= 0;
    }

    private void persist() {
        storage.queueUpForSave();
    }

    private boolean isUnconfirmed(String txId) {
        final TransactionConfidence confidenceForTxId = bsqWalletService.getConfidenceForTxId(txId);
        return confidenceForTxId != null && confidenceForTxId.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
    }

    public boolean isMine(ProtectedStoragePayload privateStoragePayload) {
        return signaturePubKey.equals(privateStoragePayload.getOwnerPubKey());
    }

    private boolean isProposalPayloadValid(ProposalPayload proposalPayload) {
        final String txId = proposalPayload.getTxId();
        Optional<Tx> optionalTx = stateService.getTx(txId);
        if (optionalTx.isPresent()) {
            final Tx tx = optionalTx.get();
            try {
                proposalPayloadValidator.validateDataFields(proposalPayload);
                proposalPayloadValidator.validateHashOfOpReturnData(proposalPayload, tx);
                // All other tx validation is done in parser, so if type is correct we know it's a correct proposalPayload tx
                proposalPayloadValidator.validateCorrectTxType(proposalPayload, tx);
                return true;
            } catch (ValidationException e) {
                log.warn("isProposalPayloadValid failed. txId={}, proposalPayload={}, validationException={}", txId, proposalPayload, e.toString());
                return false;
            }
        } else {
            log.debug("isProposalPayloadValid failed. Tx not found in stateService. txId={}", txId);
            return false;
        }
    }

    private boolean isBlindVoteValid(BlindVote blindVote) {
        final String txId = blindVote.getTxId();
        Optional<Tx> optionalTx = stateService.getTx(txId);
        if (optionalTx.isPresent()) {
            final Tx tx = optionalTx.get();
            try {
                blindVoteValidator.validateDataFields(blindVote);
                blindVoteValidator.validateHashOfOpReturnData(blindVote, tx);
                // All other tx validation is done in parser, so if type is correct we know it's a correct blindVote tx
                blindVoteValidator.validateCorrectTxType(tx);
                return true;
            } catch (ValidationException e) {
                log.warn("isBlindVoteValid failed. txId={}, blindVote={}, validationException={}", txId, blindVote, e.toString());
                return false;
            }
        } else {
            log.debug("isBlindVoteValid failed. Tx not found in stateService. txId={}", txId);
            return false;
        }
    }


    private boolean addToP2PNetwork(ProtectedStoragePayload privateStoragePayload) {
        return p2PService.addProtectedStorageEntry(privateStoragePayload, true);
    }

    private void rePublishWhenWellConnected() {
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
        getListForRepublishing().stream()
                .filter(this::isMine)
                .forEach(privateStoragePayload -> {
                    if (!addToP2PNetwork(privateStoragePayload))
                        log.warn("Adding of privateStoragePayload to P2P network failed.\nprivateStoragePayload=" + privateStoragePayload);
                });
    }
}

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
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.param.DaoParamService;
import bisq.core.dao.vote.BaseService;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.myvote.MyVoteService;
import bisq.core.dao.vote.proposal.Proposal;
import bisq.core.dao.vote.proposal.ProposalList;
import bisq.core.dao.vote.proposal.ProposalService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ExceptionHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import javax.crypto.SecretKey;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and published blind vote objects and tx as well as maintains blindVoteList.
 */
@Slf4j
public class BlindVoteService extends BaseService {
    private final ProposalService proposalService;
    private final MyVoteService myVoteService;
    private final DaoParamService daoParamService;
    private final BtcWalletService btcWalletService;
    private final Storage<BlindVoteList> storage;

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
                            ReadableBsqBlockChain readableBsqBlockChain,
                            KeyRing keyRing,
                            MyVoteService myVoteService,
                            ProposalService proposalService,
                            DaoParamService daoParamService,
                            BtcWalletService btcWalletService,
                            Storage<BlindVoteList> storage) {
        super(p2PService,
                walletsManager,
                bsqWalletService,
                periodService,
                readableBsqBlockChain,
                keyRing);

        this.proposalService = proposalService;
        this.myVoteService = myVoteService;
        this.daoParamService = daoParamService;
        this.btcWalletService = btcWalletService;

        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
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
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onRemoved(ProtectedStorageEntry protectedStorageEntry) {
        if (protectedStorageEntry.getProtectedStoragePayload() instanceof BlindVote)
            throw new UnsupportedOperationException("Removal of blind vote data is not supported");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BaseService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof BlindVote)
            addBlindVoteToList((BlindVote) protectedStoragePayload, storeLocally);
    }

    @Override
    protected void onBlockHeightChanged(int height) {
        // We display own unconfirmed blindVotes as there is no risk. Other blindVotes are only shown after they are in
        //the blockchain and verified
        validOrMyUnconfirmedBlindVotes.setPredicate(blindVote -> (isUnconfirmed(blindVote.getTxId()) &&
                isMine(blindVote)) || isValid(blindVote));
        validBlindVotes.setPredicate(this::isValid);
    }

    @Override
    protected List<ProtectedStoragePayload> getListForRepublishing() {
        return validOrMyUnconfirmedBlindVotes.stream()
                .filter(this::isMine)
                .collect(Collectors.toList());
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
            ProposalList proposalList = getSortedProposalList();
            SecretKey secretKey = BlindVoteConsensus.getSecretKey();
            byte[] encryptedProposalList = BlindVoteConsensus.getEncryptedProposalList(proposalList, secretKey);

            final byte[] hash = BlindVoteConsensus.getHashOfEncryptedProposalList(encryptedProposalList);
            log.info("Sha256Ripemd160 hash of encryptedProposalList: " + Utilities.bytesAsHexString(hash));
            byte[] opReturnData = BlindVoteConsensus.getOpReturnData(hash);
            final Coin fee = BlindVoteConsensus.getFee(daoParamService, readableBsqBlockChain.getChainHeadHeight());
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

    private void addBlindVoteToList(BlindVote blindVote, boolean storeLocally) {
        if (!observableList.contains(blindVote)) {
            log.info("We received a BlindVote from the P2P network. BlindVote=" + blindVote);
            observableList.add(blindVote);

            if (storeLocally)
                persist();

            onBlockHeightChanged(readableBsqBlockChain.getChainHeadHeight());
        } else {
            if (storeLocally && !isMine(blindVote))
                log.debug("We have that blindVote already in our list. blindVote={}", blindVote);
        }
    }

    private ProposalList getSortedProposalList() {
        // Need to clone as we cannot sort a filteredList
        List<Proposal> proposals = new ArrayList<>(proposalService.getActiveProposals());
        BlindVoteConsensus.sortProposalList(proposals);
        return new ProposalList(proposals);
    }

    private boolean hasEnoughBsqFunds(Coin stake, Coin fee) {
        return bsqWalletService.getAvailableBalance().compareTo(stake.add(fee)) >= 0;
    }

    private void persist() {
        storage.queueUpForSave();
    }
}

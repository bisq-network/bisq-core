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
import bisq.core.dao.vote.myvote.MyVoteService;
import bisq.core.dao.vote.proposal.Proposal;
import bisq.core.dao.vote.proposal.ProposalList;
import bisq.core.dao.vote.proposal.ProposalService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javax.crypto.SecretKey;

import java.security.PublicKey;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates and published blind vote objects and tx as well as maintains blindVoteList.
 */
@Slf4j
public class BlindVoteService implements PersistedDataHost, HashMapChangedListener {
    private final ProposalService proposalService;
    private final MyVoteService myVoteService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final DaoParamService daoParamService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final WalletsManager walletsManager;
    private final P2PService p2PService;
    private final PublicKey signaturePubKey;
    private final Storage<BlindVoteList> storage;

    // BlindVoteList is wrapper for persistence. From outside we access only list inside of wrapper.
    private final BlindVoteList blindVoteList = new BlindVoteList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteService(ProposalService proposalService,
                            MyVoteService myVoteService,
                            ReadableBsqBlockChain readableBsqBlockChain,
                            DaoParamService daoParamService,
                            BsqWalletService bsqWalletService,
                            BtcWalletService btcWalletService,
                            WalletsManager walletsManager,
                            P2PService p2PService,
                            KeyRing keyRing,
                            Storage<BlindVoteList> storage) {
        this.proposalService = proposalService;
        this.myVoteService = myVoteService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.daoParamService = daoParamService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.walletsManager = walletsManager;
        this.p2PService = p2PService;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
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
                this.blindVoteList.clear();
                this.blindVoteList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.addHashSetChangedListener(this);
        p2PService.getDataMap().values().forEach(this::onAdded);
    }

    @SuppressWarnings("EmptyMethod")
    public void shutDown() {
        // TODO keep for later, maybe we get resources to clean up later
    }

    public void publishBlindVote(Coin stake, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler)
            throws CryptoException, InsufficientMoneyException, WalletException, TransactionVerificationException,
            IOException {
        ProposalList proposalList = getSortedProposalList();
        SecretKey secretKey = BlindVoteConsensus.getSecretKey();
        byte[] encryptedProposalList = BlindVoteConsensus.getEncryptedProposalList(proposalList, secretKey);
        final byte[] hash = BlindVoteConsensus.getHashOfEncryptedProposalList(encryptedProposalList);
        byte[] opReturnData = BlindVoteConsensus.getOpReturnData(hash);
        final Coin fee = BlindVoteConsensus.getFee(daoParamService, readableBsqBlockChain.getChainHeadHeight());
        final Transaction blindVoteTx = getBlindVoteTx(stake, fee, opReturnData);
        walletsManager.publishAndCommitBsqTx(blindVoteTx, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                BlindVote blindVote = new BlindVote(encryptedProposalList, blindVoteTx.getHashAsString(), stake.value, signaturePubKey);
                addBlindVote(blindVote);

                if (p2PService.addProtectedStorageEntry(blindVote, true)) {
                    log.info("Added blindVote to P2P network.\nblindVote={}", blindVote);
                    resultHandler.handleResult();
                } else {
                    final String msg = "Adding of blindVote to P2P network failed.\nblindVote=" + blindVote;
                    log.error(msg);
                    errorMessageHandler.handleErrorMessage(msg);
                }

                myVoteService.applyNewBlindVote(proposalList, secretKey, blindVote);
            }

            @Override
            public void onTimeout(TxBroadcastTimeoutException exception) {
                // TODO handle
                // We need to handle cases where a timeout happens and
                // the tx might get broadcasted at a later restart!
                // We need to be sure that in case of a failed tx the locked stake gets unlocked!
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }

            @Override
            public void onTxMalleability(TxMalleabilityException exception) {
                // TODO handle
                // We need to be sure that in case of a failed tx the locked stake gets unlocked!
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                // TODO handle
                // We need to be sure that in case of a failed tx the locked stake gets unlocked!
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }
        });
    }

    public List<BlindVote> getBlindVoteList() {
        return blindVoteList.getList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry protectedStorageEntry) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof BlindVote) {
            addBlindVote((BlindVote) protectedStoragePayload);
        }
    }

    @Override
    public void onRemoved(ProtectedStorageEntry protectedStorageEntry) {
        if (protectedStorageEntry.getProtectedStoragePayload() instanceof BlindVote)
            throw new UnsupportedOperationException("Removal of blind vote data is not supported");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBlindVote(BlindVote blindVote) {
        if (!blindVoteList.contains(blindVote)) {
            blindVoteList.add(blindVote);
            persist();
            log.info("Added blindVote to blindVoteList.\nblindVote={}", blindVote);
        } else {
            log.warn("We have that blindVote already in our list. blindVote={}", blindVote);
        }
    }

    private ProposalList getSortedProposalList() {
        // Need to clone as we cannot sort a filteredList
        List<Proposal> proposals = new ArrayList<>(proposalService.getActiveProposals());
        BlindVoteConsensus.sortProposalList(proposals);
        return new ProposalList(proposals);
    }

    private Transaction getBlindVoteTx(Coin stake, Coin voteFee, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedBlindVoteTx(voteFee, stake);
        Transaction txWithBtcFee = btcWalletService.completePreparedBlindVoteTx(preparedTx, opReturnData);
        final Transaction tx = bsqWalletService.signTx(txWithBtcFee);
        log.info("blindVoteTx={}", tx);
        return tx;
    }

    private void persist() {
        storage.queueUpForSave();
    }
}

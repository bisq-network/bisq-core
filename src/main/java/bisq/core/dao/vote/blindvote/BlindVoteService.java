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
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;

import javax.crypto.SecretKey;

import java.security.PublicKey;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates and published blind vote objects and maintains list.
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

    public void publishBlindVote(Coin stake, FutureCallback<Transaction> callback)
            throws CryptoException, InsufficientMoneyException, WalletException, TransactionVerificationException,
            IOException {
        ProposalList proposalList = getSortedProposalList();
        SecretKey secretKey = BlindVoteConsensus.getSecretKey();
        byte[] encryptedProposals = BlindVoteConsensus.getEncryptedProposalList(proposalList, secretKey);
        byte[] opReturnData = BlindVoteConsensus.getOpReturnData(encryptedProposals);
        final Transaction blindVoteTx = getBlindVoteTx(stake, opReturnData);

        // TODO not updated to TxBroadcaster changes because not in sync with voting branch anyway
       /* walletsManager.publishAndCommitBsqTx(blindVoteTx, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
                //TODO set a flag once the tx was successfully broadcasted
                // Also verify that the txId is as expected and not got changed (malleability)
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                //TODO handle error
                callback.onFailure(t);
            }
        });*/

        // We cannot apply the state change only if we get the tx successfully broadcasted.
        // In case the broadcast fails or timed out we might get the tx broadcasted at another startup
        // but we would not get the blind vote recognized as we did not applied the state changes.
        // That is particularly problematic as the stake is locked up when the OP_RETURN data are processed in
        // the parsing. We prefer to keep track of the state and notify the user in case the tx broadcast failed or
        // timed out. Any time the tx succeeds broadcast later the blind vote becomes valid.
        BlindVote blindVote = new BlindVote(encryptedProposals, blindVoteTx.getHashAsString(), stake.value, signaturePubKey);
        addBlindVote(blindVote);
        addBlindVoteToP2PNetwork(blindVote);
        myVoteService.applyNewBlindVote(proposalList, secretKey, blindVote);
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

    private Transaction getBlindVoteTx(Coin stake, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        final Coin voteFee = BlindVoteConsensus.getFee(daoParamService, readableBsqBlockChain);
        Transaction preparedTx = bsqWalletService.getPreparedBlindVoteTx(voteFee, stake);
        Transaction txWithBtcFee = btcWalletService.completePreparedBlindVoteTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }

    private boolean addBlindVoteToP2PNetwork(BlindVote blindVote) {
        final boolean success = p2PService.addProtectedStorageEntry(blindVote, true);
        if (success) {
            log.info("Added blindVote to P2P network.\nblindVote={}", blindVote);
        } else { //TODO handle error
            log.warn("We could not publish the blind vote to the P2P network. blindVote={}", blindVote);
        }
        return success;
    }

    private void persist() {
        storage.queueUpForSave();
    }
}

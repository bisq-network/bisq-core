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
import bisq.core.dao.vote.DaoPeriodService;
import bisq.core.dao.vote.MyVote;
import bisq.core.dao.vote.MyVoteList;
import bisq.core.dao.vote.consensus.BlindVoteConsensus;
import bisq.core.dao.vote.proposal.Proposal;
import bisq.core.dao.vote.proposal.ProposalCollectionsService;
import bisq.core.dao.vote.proposal.ProposalList;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javax.crypto.SecretKey;

import java.security.PublicKey;

import java.io.IOException;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Creates and published blind votes and manages the vote lists.
 */
@Slf4j
public class BlindVoteService implements PersistedDataHost, HashMapChangedListener {
    private final ProposalCollectionsService proposalCollectionsService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final DaoPeriodService daoPeriodService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final WalletsManager walletsManager;
    private final P2PService p2PService;
    private final PublicKey signaturePubKey;
    private final Storage<MyVoteList> myVoteListStorage;
    private final Storage<BlindVoteList> blindVoteListStorage;

    @Getter
    private final ObservableList<MyVote> myVotesList = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<BlindVote> blindVoteList = FXCollections.observableArrayList();
    @Getter
    private final List<BlindVote> blindVoteSortedList = new SortedList<>(blindVoteList);
    private ChangeListener<Number> numConnectedPeersListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteService(ProposalCollectionsService proposalCollectionsService,
                            ReadableBsqBlockChain readableBsqBlockChain,
                            DaoPeriodService daoPeriodService,
                            BsqWalletService bsqWalletService,
                            BtcWalletService btcWalletService,
                            WalletsManager walletsManager,
                            P2PService p2PService,
                            KeyRing keyRing,
                            Storage<MyVoteList> myVoteListStorage,
                            Storage<BlindVoteList> blindVoteListStorage) {
        this.proposalCollectionsService = proposalCollectionsService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.daoPeriodService = daoPeriodService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.walletsManager = walletsManager;
        this.p2PService = p2PService;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
        this.myVoteListStorage = myVoteListStorage;
        this.blindVoteListStorage = blindVoteListStorage;

        blindVoteSortedList.sort(BlindVoteConsensus.getBlindVoteListComparator());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            MyVoteList persistedMyVotes = myVoteListStorage.initAndGetPersistedWithFileName("MyVoteList", 100);
            if (persistedMyVotes != null) {
                this.myVotesList.clear();
                this.myVotesList.addAll(persistedMyVotes.getList());
            }

            BlindVoteList persistedBlindVotes = blindVoteListStorage.initAndGetPersistedWithFileName("BlindVoteList", 100);
            if (persistedBlindVotes != null) {
                this.blindVoteList.clear();
                this.blindVoteList.addAll(persistedBlindVotes.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.addHashSetChangedListener(this);
        p2PService.getDataMap().values().forEach(this::onAdded);

        // Republish own active blindVotes once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> {
            publishMyBlindVotesIfWellConnected();
        };
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
        publishMyBlindVotesIfWellConnected();
    }

    @SuppressWarnings("EmptyMethod")
    public void shutDown() {
        // TODO keep for later, maybe we get resources to clean up later
    }

    public void publishBlindVote(Coin stake, FutureCallback<Transaction> callback)
            throws CryptoException, InsufficientMoneyException, WalletException, TransactionVerificationException,
            IOException {
        ProposalList proposalList = getClonedProposalList(proposalCollectionsService.getActiveProposals());
        SecretKey secretKey = BlindVoteConsensus.getSecretKey();
        byte[] encryptedProposals = BlindVoteConsensus.getEncryptedProposalList(proposalList, secretKey);
        byte[] opReturnData = BlindVoteConsensus.getOpReturnData(encryptedProposals);
        final Transaction blindVoteTx = getBlindVoteTx(stake, opReturnData);
        walletsManager.publishAndCommitBsqTx(blindVoteTx, new FutureCallback<Transaction>() {
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
        });

        // We cannot apply the state change only if we get the tx successfully broadcasted.
        // In case the broadcast fails or timed out we might get the tx broadcasted at another startup
        // but we would not get the blind vote recognized as we did not applied the state changes.
        // That is particularly problematic as the stake is locked up when the OP_RETURN data are processed in
        // the parsing. We prefer to keep track of the state and notify the user in case the tx broadcast failed or
        // timed out. Any time the tx succeeds broadcast later the blind vote becomes valid.
        BlindVote blindVote = new BlindVote(encryptedProposals, blindVoteTx.getHashAsString(), stake.value, signaturePubKey);
        if (!blindVoteList.contains(blindVote)) {
            blindVoteList.add(blindVote);
            blindVoteListStorage.queueUpForSave(new BlindVoteList(blindVoteList), 100);
        } else {
            log.warn("We have that blindVote already in our list. blindVote={}", blindVote);
        }
        if (!addBlindVoteToP2PNetwork(blindVote)) {
            //TODO handle error
            log.warn("We could not publish the blind vote to the P2P network. blindVote={}", blindVote);
        }

        MyVote myVote = new MyVote(proposalList, Utils.HEX.encode(secretKey.getEncoded()), blindVote);
        myVotesList.add(myVote);
        myVoteListStorage.queueUpForSave(new MyVoteList(myVotesList), 100);
    }

    public List<BlindVote> getBlindVoteListForCurrentCycle() {
        return blindVoteSortedList.stream()
                .filter(blindVote -> daoPeriodService.isTxInCurrentCycle(blindVote.getTxId()))
                .collect(Collectors.toList());
    }

    public void persistMyVoteListStorage() {
        myVoteListStorage.queueUpForSave();
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
    public void onRemoved(ProtectedStorageEntry data) {
        throw new UnsupportedOperationException("Removal of blind vote data is not supported");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBlindVote(BlindVote blindVote) {
        if (!blindVoteList.contains(blindVote)) {
            blindVoteList.add(blindVote);
            blindVoteListStorage.queueUpForSave(new BlindVoteList(blindVoteList), 100);
        } else {
            log.warn("We have that item in our list already");
        }
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
        myVotesList.stream()
                .filter(vote -> daoPeriodService.isTxInPhase(vote.getTxId(), DaoPeriodService.Phase.BLIND_VOTE))
                .forEach(vote -> addBlindVoteToP2PNetwork(vote.getBlindVote()));
    }

    private ProposalList getClonedProposalList(FilteredList<Proposal> proposals) throws InvalidProtocolBufferException {
        ProposalList cloned = ProposalList.clone(new ProposalList(proposals));
        final List<Proposal> sortedProposals = BlindVoteConsensus.getSortedProposalList(cloned.getList());
        return new ProposalList(sortedProposals);
    }

    private Transaction getBlindVoteTx(Coin stake, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        final Coin voteFee = BlindVoteConsensus.getFee(readableBsqBlockChain);
        Transaction preparedTx = bsqWalletService.getPreparedBlindVoteTx(voteFee, stake);
        Transaction txWithBtcFee = btcWalletService.completePreparedBlindVoteTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }

    private boolean addBlindVoteToP2PNetwork(BlindVote blindVote) {
        return p2PService.addProtectedStorageEntry(blindVote, true);
    }
}

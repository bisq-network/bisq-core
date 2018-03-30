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

package bisq.core.dao.vote;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoPeriodService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.proposal.Proposal;
import bisq.core.dao.proposal.ProposalCollectionsService;
import bisq.core.dao.proposal.ProposalList;
import bisq.core.dao.vote.consensus.BlindVoteConsensus;
import bisq.core.dao.vote.consensus.VoteRevealConsensus;

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
import bisq.common.util.Utilities;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Creates and published blind votes and manages the vote list.
 */
@Slf4j
public class VoteService implements PersistedDataHost, HashMapChangedListener {
    private final ProposalCollectionsService proposalCollectionsService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final DaoPeriodService daoPeriodService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final P2PService p2PService;
    private final PublicKey signaturePubKey;
    private final Storage<MyVoteList> myVoteListStorage;
    private final Storage<BlindVoteList> blindVoteListStorage;

    @Getter
    private final List<MyVote> myVotesList = new ArrayList<>();
    @Getter
    private final ObservableList<BlindVote> blindVoteList = FXCollections.observableArrayList();
    @Getter
    private final List<BlindVote> blindVoteSortedList = new SortedList<>(blindVoteList);
    private ChangeListener<Number> numConnectedPeersListener;
    private BsqBlockChain.Listener bsqBlockChainListener;
    private ObservableList<Throwable> errors = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteService(ProposalCollectionsService proposalCollectionsService,
                       ReadableBsqBlockChain readableBsqBlockChain,
                       DaoPeriodService daoPeriodService,
                       BsqWalletService bsqWalletService,
                       BtcWalletService btcWalletService,
                       P2PService p2PService,
                       KeyRing keyRing,
                       Storage<MyVoteList> myVoteListStorage,
                       Storage<BlindVoteList> blindVoteListStorage) {
        this.proposalCollectionsService = proposalCollectionsService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.daoPeriodService = daoPeriodService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
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
        // Listen to BlindVote data from the network
        p2PService.addHashSetChangedListener(this);
        p2PService.getDataMap().values().forEach(this::onAdded);

        // Listen to phase changes
        daoPeriodService.getPhaseProperty().addListener((observable, oldValue, newValue) -> {
            onPhaseChanged(newValue);
        });
        onPhaseChanged(daoPeriodService.getPhaseProperty().get());

        // Republish own active blindVotes once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> {
            publishMyBlindVotesIfWellConnected();
        };
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
        publishMyBlindVotesIfWellConnected();
    }

    public void shutDown() {
        // TODO keep for later, maybe we get resources to clean up later
    }

    public void publishBlindVote(Coin stake, FutureCallback<Transaction> callback)
            throws CryptoException, InsufficientMoneyException, WalletException, TransactionVerificationException,
            IOException {
        ProposalList proposalList = getClonedProposalList(proposalCollectionsService.getActiveProposals());
        SecretKey secretKey = BlindVoteConsensus.getSecretKey();
        byte[] encryptedProposalList = BlindVoteConsensus.getEncryptedProposalList(proposalList, secretKey);
        byte[] opReturnData = BlindVoteConsensus.getOpReturnData(encryptedProposalList);
        final Transaction blindVoteTx = getBlindVoteTx(stake, opReturnData);
        publishBlindVoteTx(blindVoteTx, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                callback.onFailure(t);
            }
        });

        BlindVote blindVote = new BlindVote(encryptedProposalList, blindVoteTx.getHashAsString(), stake.value, signaturePubKey);
        addBlindVoteToP2PNetwork(blindVote);
        if (!blindVoteList.contains(blindVote)) {
            blindVoteList.add(blindVote);
            blindVoteListStorage.queueUpForSave(new BlindVoteList(blindVoteList), 100);
        }

        MyVote myVote = new MyVote(proposalList, Utils.HEX.encode(secretKey.getEncoded()), blindVote);
        myVotesList.add(myVote);
        myVoteListStorage.queueUpForSave(new MyVoteList(myVotesList), 100);
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
        // Removal is not supported
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

    private void publishBlindVoteTx(Transaction blindVoteTx, FutureCallback<Transaction> callback) {
        // We need to create another instance, otherwise the tx would trigger an invalid state exception
        // if it gets committed 2 times
        // We clone before commit to avoid unwanted side effects
        final Transaction clonedTx = btcWalletService.getClonedTransaction(blindVoteTx);
        btcWalletService.commitTx(clonedTx);
        bsqWalletService.commitTx(blindVoteTx);
        bsqWalletService.broadcastTx(blindVoteTx, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction transaction) {
                checkNotNull(transaction, "Transaction must not be null at broadcastTx callback.");
                callback.onSuccess(transaction);
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    private void addBlindVoteToP2PNetwork(BlindVote blindVote) {
        p2PService.addProtectedStorageEntry(blindVote, true);
    }

    private void onPhaseChanged(DaoPeriodService.Phase phase) {
        if (phase == DaoPeriodService.Phase.VOTE_REVEAL) {
            // A phase change is triggered by a new block but we need to wait for the parser to complete
            //TODO use handler only triggered at end of parsing. -> Refactor bsqBlockChain and BsqNode handlers
            bsqBlockChainListener = bsqBlock -> revealVotes();
            readableBsqBlockChain.addListener(bsqBlockChainListener);
            revealVotes();
        } else {
            // If we are not in the reveal phase we are not interested in the events.
            if (bsqBlockChainListener != null)
                readableBsqBlockChain.removeListener(bsqBlockChainListener);
            bsqBlockChainListener = null;
        }
    }

    private void revealVotes() {
        myVotesList.stream()
                .filter(myVote -> myVote.getRevealTxId() == null)
                .forEach(myVote -> {
                    // We prefer to handle the exception here inside the stream iteration
                    try {
                        revealVote(myVote);
                    } catch (IOException e) {
                        e.printStackTrace();
                        log.error(e.toString());
                        //TODO wrap to specific exception
                        errors.add(e);
                    }
                });
    }

    private void revealVote(MyVote myVote) throws IOException {
        byte[] hashOfBlindVoteList = VoteRevealConsensus.getHashOfBlindVoteList(blindVoteSortedList);
        log.info("Sha256Ripemd160 hash of hashOfBlindVoteList " + Utilities.bytesAsHexString(hashOfBlindVoteList));
        byte[] opReturnData = VoteRevealConsensus.getOpReturnData(hashOfBlindVoteList, myVote.getSecretKey());

        final Set<TxOutput> lockedForVoteTxOutputs = readableBsqBlockChain.getLockedForVoteTxOutputs();
        Optional<TxOutput> optionalStakeTxOutput = lockedForVoteTxOutputs.stream()
                .filter(txOutput -> txOutput.getTxId().equals(myVote.getTxId()))
                .findAny();
        if (optionalStakeTxOutput.isPresent()) {
            try {
                final TxOutput stakeTxOutput = optionalStakeTxOutput.get();
                VoteRevealConsensus.unlockStakeTxOutputType(stakeTxOutput);
                Transaction voteRevealTx = getVoteRevealTx(stakeTxOutput, opReturnData);
                myVote.setRevealTxId(voteRevealTx.getHashAsString());
                myVoteListStorage.queueUpForSave();

                publishRevealTx(voteRevealTx, new FutureCallback<Transaction>() {
                    @Override
                    public void onSuccess(@Nullable Transaction result) {
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t) {
                    }
                });
            } catch (InsufficientMoneyException e) {
                e.printStackTrace();
            } catch (WalletException e) {
                e.printStackTrace();
            } catch (TransactionVerificationException e) {
                e.printStackTrace();
            }
        } else {
            //log.warn("optionalStakeTxOutput is not present. myVote={}", myVote);
        }
    }

    private Transaction getVoteRevealTx(TxOutput stakeTxOutput, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException,
            TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedVoteRevealTx(stakeTxOutput);
        checkArgument(preparedTx.getInputs().size() == 1, "preparedTx num inputs must be 1");
        checkArgument(preparedTx.getOutputs().size() == 1, "preparedTx num outputs must be 1");
        Transaction txWithBtcFee = btcWalletService.completePreparedVoteRevealTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }

    //TODO is same as for blind vote tx
    private void publishRevealTx(Transaction voteRevealTx, FutureCallback<Transaction> callback) {
        // We need to create another instance, otherwise the tx would trigger an invalid state exception
        // if it gets committed 2 times
        // We clone before commit to avoid unwanted side effects
        final Transaction clonedTx = btcWalletService.getClonedTransaction(voteRevealTx);
        btcWalletService.commitTx(clonedTx);

        bsqWalletService.commitTx(voteRevealTx);

        bsqWalletService.broadcastTx(voteRevealTx, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction transaction) {
                checkNotNull(transaction, "Transaction must not be null at broadcastTx callback.");

                callback.onSuccess(transaction);
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                callback.onFailure(t);
            }
        });
    }
}

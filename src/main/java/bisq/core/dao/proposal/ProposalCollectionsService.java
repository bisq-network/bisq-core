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

package bisq.core.dao.proposal;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoPeriodService;
import bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import bisq.core.dao.blockchain.BsqBlockChainListener;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.proposal.compensation.CompensationRequest;
import bisq.core.dao.proposal.generic.GenericProposal;
import bisq.core.provider.fee.FeeService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.security.PublicKey;

import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Manages proposal collections.
 */
@Slf4j
public class ProposalCollectionsService implements PersistedDataHost, BsqBlockChainListener, HashMapChangedListener {
    private final P2PService p2PService;
    private final DaoPeriodService daoPeriodService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final Storage<ProposalList> proposalListStorage;
    private final PublicKey signaturePubKey;
    private final FeeService feeService;
    @Getter
    private final ObservableList<Proposal> allProposals = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<Proposal> activeProposals = new FilteredList<>(allProposals);
    @Getter
    private final FilteredList<Proposal> closedProposals = new FilteredList<>(allProposals);
    private ChangeListener<Number> numConnectedPeersListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalCollectionsService(P2PService p2PService,
                                      BsqWalletService bsqWalletService,
                                      BtcWalletService btcWalletService,
                                      DaoPeriodService daoPeriodService,
                                      ReadableBsqBlockChain readableBsqBlockChain,
                                      BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                      KeyRing keyRing,
                                      Storage<ProposalList> proposalListStorage,
                                      FeeService feeService) {
        this.p2PService = p2PService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.daoPeriodService = daoPeriodService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.proposalListStorage = proposalListStorage;
        this.feeService = feeService;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
        bsqBlockChainChangeDispatcher.addBsqBlockChainListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            ProposalList persisted = proposalListStorage.initAndGetPersistedWithFileName("ProposalList", 100);
            if (persisted != null) {
                this.allProposals.clear();
                this.allProposals.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry data) {
        final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload)
            addProposal((ProposalPayload) protectedStoragePayload, true);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry data) {
        final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            findProposal((ProposalPayload) protectedStoragePayload).ifPresent(proposal -> {
                if (isInPhaseOrUnconfirmed(proposal.getProposalPayload())) {
                    removeProposalFromList(proposal);
                } else {
                    final String msg = "onRemoved called of a Proposal which is outside of the Request phase is invalid and we ignore it.";
                    log.warn(msg);
                    if (DevEnv.isDevMode())
                        throw new RuntimeException(msg);
                }
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBlockChainListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBsqBlockChainChanged() {
        // not needed with current impl. but leave it as updatePredicates might change
        // updatePredicates();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.addHashSetChangedListener(this);

        // At startup the P2PDataStorage initializes earlier, otherwise we get the listener called.
        p2PService.getP2PDataStorage().getMap().values().forEach(e -> {
            final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
            if (protectedStoragePayload instanceof ProposalPayload)
                addProposal((ProposalPayload) protectedStoragePayload, false);
        });

        // Republish own active proposals once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> {
            UserThread.runAfter(() -> rebroadcastBlindVotes((int) newValue), 2);
        };
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);

        bsqWalletService.getChainHeightProperty().addListener((observable, oldValue, newValue) -> {
            onChainHeightChanged();
        });
        onChainHeightChanged();
    }

    private void rebroadcastBlindVotes(int numConnectedPeers) {
        if ((numConnectedPeers > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
            p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
            activeProposals.stream()
                    .filter(this::isMine)
                    .forEach(e -> addToP2PNetwork(e.getProposalPayload()));
        }
    }

    public void shutDown() {
    }

    public void publishProposal(Proposal proposal, FutureCallback<Transaction> callback) {
        // We need to create another instance, otherwise the tx would trigger an invalid state exception
        // if it gets committed 2 times
        // We clone before commit to avoid unwanted side effects
        final Transaction tx = proposal.getTx();
        final Transaction clonedTx = btcWalletService.getClonedTransaction(tx);
        btcWalletService.commitTx(clonedTx);

        bsqWalletService.commitTx(tx);

        bsqWalletService.broadcastTx(tx, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction transaction) {
                checkNotNull(transaction, "Transaction must not be null at broadcastTx callback.");
                proposal.getProposalPayload().setTxId(transaction.getHashAsString());
                addToP2PNetwork(proposal.getProposalPayload());

                queueUpForSave();

                callback.onSuccess(transaction);
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public boolean removeProposal(Proposal proposal) {
        final ProposalPayload proposalPayload = proposal.getProposalPayload();
        // We allow removal which are not confirmed yet or if it we are in the right phase
        if (isMine(proposal)) {
            if (isInPhaseOrUnconfirmed(proposalPayload)) {
                final boolean success = p2PService.removeData(proposalPayload, true);
                if (success)
                    removeProposalFromList(proposal);
                else
                    log.warn("Could not remove proposal from p2p network. proposal={}", proposal);

                return success;
            } else {
                final String msg = "removeProposal called with a Proposal which is outside of the Proposal phase.";
                log.warn(msg);
                if (DevEnv.isDevMode())
                    throw new RuntimeException(msg);
                return false;
            }
        } else {
            final String msg = "removeProposal called for a Proposal which is not ours. That must not happen.";
            log.error(msg);
            if (DevEnv.isDevMode())
                throw new RuntimeException(msg);
            return false;
        }
    }

    private void removeProposalFromList(Proposal proposal) {
        removeFromList(proposal);
        queueUpForSave();
    }

    public void queueUpForSave() {
        proposalListStorage.queueUpForSave(new ProposalList(allProposals), 100);
    }

    public boolean isMine(Proposal proposal) {
        return isMine(proposal.getProposalPayload());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addToP2PNetwork(ProposalPayload proposalPayload) {
        p2PService.addProtectedStorageEntry(proposalPayload, true);
    }

    private boolean isInPhaseOrUnconfirmed(ProposalPayload payload) {
        return readableBsqBlockChain.getTxMap().get(payload.getTxId()) == null ||
                daoPeriodService.isTxInPhase(payload.getTxId(), DaoPeriodService.Phase.PROPOSAL);
    }

    private boolean isMine(ProposalPayload proposalPayload) {
        return signaturePubKey.equals(proposalPayload.getOwnerPubKey());
    }

    private void onChainHeightChanged() {
        updatePredicates();
    }

    private void addProposal(ProposalPayload proposalPayload, boolean storeLocally) {
        if (!contains(proposalPayload)) {
            allProposals.add(getProposal(proposalPayload));
            updatePredicates();

            if (storeLocally)
                queueUpForSave();
        } else {
            if (!isMine(proposalPayload))
                log.warn("We already have an item with the same Proposal.");
        }
    }

    @NotNull
    private Proposal getProposal(ProposalPayload proposalPayload) {
        switch (proposalPayload.getType()) {
            case COMPENSATION_REQUEST:
                return new CompensationRequest(proposalPayload);
            case GENERIC:
                return new GenericProposal(proposalPayload);
            case CHANGE_PARAM:
                //TODO
                throw new RuntimeException("Not implemented yet");
            case REMOVE_ALTCOIN:
                //TODO
                throw new RuntimeException("Not implemented yet");
            default:
                final String msg = "Undefined ProposalType " + proposalPayload.getType();
                log.error(msg);
                throw new RuntimeException(msg);
        }
    }

    private void updatePredicates() {
        activeProposals.setPredicate(proposal -> !daoPeriodService.isTxInPastCycle(proposal.getTxId()));
        closedProposals.setPredicate(proposal -> daoPeriodService.isTxInPastCycle(proposal.getTxId()));
    }

    private boolean contains(ProposalPayload proposalPayload) {
        return findProposal(proposalPayload).isPresent();
    }

    private Optional<Proposal> findProposal(ProposalPayload proposalPayload) {
        return allProposals.stream().filter(e -> e.getProposalPayload().equals(proposalPayload)).findAny();
    }

    private void removeFromList(Proposal proposal) {
        allProposals.remove(proposal);
        updatePredicates();
    }
}

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

package bisq.core.dao.vote.proposal;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.proposal.compensation.CompensationRequest;
import bisq.core.dao.vote.proposal.generic.GenericProposal;

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
public class ProposalService implements PersistedDataHost, BsqBlockChain.Listener, HashMapChangedListener {
    private final P2PService p2PService;
    private final BsqWalletService bsqWalletService;
    private final WalletsManager walletsManager;
    private final PeriodService periodService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final Storage<ProposalList> proposalListStorage;
    private final PublicKey signaturePubKey;
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
    public ProposalService(P2PService p2PService,
                           BsqWalletService bsqWalletService,
                           WalletsManager walletsManager,
                           PeriodService periodService,
                           ReadableBsqBlockChain readableBsqBlockChain,
                           KeyRing keyRing,
                           Storage<ProposalList> proposalListStorage) {
        this.p2PService = p2PService;
        this.bsqWalletService = bsqWalletService;
        this.walletsManager = walletsManager;
        this.periodService = periodService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.proposalListStorage = proposalListStorage;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
        readableBsqBlockChain.addListener(this);
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
        onProtectedStorageEntry(data, true);
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
    // BsqBlockChain.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        // TODO
        // not needed with current impl. but leave it as updatePredicates might change
        // updatePredicates();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().getMap().values().forEach(e -> onProtectedStorageEntry(e, false));

        // Republish own active proposals once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> {
            final int numConnectedPeers = (int) newValue;
            if (isReadyForRepublish(numConnectedPeers))
                republishProposal();
        };
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
        final int numConnectedPeers = p2PService.getNumConnectedPeers().get();
        if (isReadyForRepublish(numConnectedPeers))
            republishProposal();

        bsqWalletService.getChainHeightProperty().addListener((observable, oldValue, newValue) -> onChainHeightChanged());
        onChainHeightChanged();
    }

    public void shutDown() {
    }

    public void publishProposal(Proposal proposal, FutureCallback<Transaction> callback) {
        final Transaction proposalTx = proposal.getTx();
        checkNotNull(proposalTx, "proposal.getTx() at publishProposal callback must not be null");
        walletsManager.publishAndCommitBsqTx(proposalTx, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction transaction) {
                try {
                    checkNotNull(transaction, "transaction at publishProposal callback must not be null");
                    final String txId = transaction.getHashAsString();
                    if (!txId.equals(proposalTx.getHashAsString())) {
                        log.warn("We received a different tx ID as we had in our proposal. " +
                                        "That might be a caused due tx malleability. " +
                                        "proposal.getTx().getHashAsString()={}, transaction.getHashAsString()={}",
                                proposalTx.getHashAsString(),
                                txId);
                    }
                    proposal.getProposalPayload().setTxId(txId);

                    addToP2PNetwork(proposal.getProposalPayload());
                    callback.onSuccess(transaction);
                } catch (Throwable t) {
                    callback.onFailure(t);
                }
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

    public void persist() {
        proposalListStorage.queueUpForSave(new ProposalList(allProposals), 100);
    }

    public boolean isMine(Proposal proposal) {
        return isMine(proposal.getProposalPayload());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload)
            addProposal((ProposalPayload) protectedStoragePayload, storeLocally);
    }

    private void addToP2PNetwork(ProposalPayload proposalPayload) {
        p2PService.addProtectedStorageEntry(proposalPayload, true);
    }

    private boolean isInPhaseOrUnconfirmed(ProposalPayload payload) {
        return readableBsqBlockChain.getTxMap().get(payload.getTxId()) == null ||
                periodService.isTxInPhase(payload.getTxId(), PeriodService.Phase.PROPOSAL);
    }

    private boolean isMine(ProposalPayload proposalPayload) {
        return signaturePubKey.equals(proposalPayload.getOwnerPubKey());
    }

    private boolean isReadyForRepublish(int numConnectedPeers) {
        return (numConnectedPeers > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode();
    }

    private void republishProposal() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false
        UserThread.runAfter(() -> {
            p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
            activeProposals.stream()
                    .filter(this::isMine)
                    .forEach(e -> addToP2PNetwork(e.getProposalPayload()));
        }, 2);
    }


    private void onChainHeightChanged() {
        updatePredicates();
    }

    private void addProposal(ProposalPayload proposalPayload, boolean storeLocally) {
        if (!contains(proposalPayload)) {
            allProposals.add(getProposal(proposalPayload));
            updatePredicates();

            if (storeLocally)
                persist();
        } else {
            if (!isMine(proposalPayload))
                log.warn("We already have an item with the same Proposal.");
        }
    }

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
        activeProposals.setPredicate(proposal -> !periodService.isTxInPastCycle(proposal.getTxId()));
        closedProposals.setPredicate(proposal -> periodService.isTxInPastCycle(proposal.getTxId()));
    }

    private boolean contains(ProposalPayload proposalPayload) {
        return findProposal(proposalPayload).isPresent();
    }

    private Optional<Proposal> findProposal(ProposalPayload proposalPayload) {
        return allProposals.stream().filter(e -> e.getProposalPayload().equals(proposalPayload)).findAny();
    }

    private void removeProposalFromList(Proposal proposal) {
        allProposals.remove(proposal);
        // TODO
        // updatePredicates();
        persist();
    }
}

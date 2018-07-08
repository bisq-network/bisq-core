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
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.security.PublicKey;

import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Manages proposal collections.
 */
@Slf4j
public class ProposalService implements PersistedDataHost, HashMapChangedListener, BsqBlockChain.Listener {
    private final P2PService p2PService;
    private final WalletsManager walletsManager;
    private final PeriodService periodService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final Storage<ProposalList> storage;
    private final PublicKey signaturePubKey;

    @Getter
    private final ObservableList<Proposal> observableList = FXCollections.observableArrayList();
    private final ProposalList proposalList = new ProposalList(observableList);
    @Getter
    private final FilteredList<Proposal> activeProposals = new FilteredList<>(observableList);
    @Getter
    private final FilteredList<Proposal> closedProposals = new FilteredList<>(observableList);

    private ChangeListener<Number> numConnectedPeersListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalService(P2PService p2PService,
                           WalletsManager walletsManager,
                           PeriodService periodService,
                           ReadableBsqBlockChain readableBsqBlockChain,
                           KeyRing keyRing,
                           Storage<ProposalList> storage) {
        this.p2PService = p2PService;
        this.walletsManager = walletsManager;
        this.periodService = periodService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.storage = storage;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
        readableBsqBlockChain.addListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            ProposalList persisted = storage.initAndGetPersisted(proposalList, 20);
            if (persisted != null) {
                this.observableList.clear();
                this.observableList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBlockChain.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        upDatePredicate();
    }

    private void upDatePredicate() {
        activeProposals.setPredicate(proposal -> periodService.isTxInCurrentCycle(proposal.getTxId()));
        closedProposals.setPredicate(proposal -> periodService.isTxInPastCycle(proposal.getTxId()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry entry) {
        onProtectedStorageEntry(entry, true);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry data) {
        final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            findProposal((ProposalPayload) protectedStoragePayload)
                    .ifPresent(proposal -> {
                        if (isInPhaseOrUnconfirmed(proposal.getProposalPayload())) {
                            removeProposalFromList(proposal);
                        } else {
                            final String msg = "onRemoved called of a Proposal which is outside of the Request phase is invalid and we ignore it.";
                            DevEnv.logErrorAndThrowIfDevMode(msg);
                        }
                    });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().getMap().values()
                .forEach(entry -> onProtectedStorageEntry(entry, false));

        // Republish own active proposals once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> publishMyProposalsIfWellConnected();
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
        publishMyProposalsIfWellConnected();
    }

    public void shutDown() {
    }

    public void publishProposal(Proposal proposal, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        final Transaction proposalTx = proposal.getTx();
        checkNotNull(proposalTx, "proposal.getTx() at publishProposal must not be null");
        // TODO not updated to TxBroadcaster changes because not in sync with voting branch anyway
        /* walletsManager.publishAndCommitBsqTx(proposalTx, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction transaction) {
                try {
                    checkNotNull(transaction, "transaction at publishProposal callback must not be null");
                    final String txId = transaction.getHashAsString();
                    if (txId.equals(proposalTx.getHashAsString())) {
                        final ProposalPayload proposalPayload = proposal.getProposalPayload();
                        proposalPayload.setTxId(txId);
                        if (addToP2PNetwork(proposalPayload)) {
                            log.info("Added proposalPayload to P2P network.\nproposalPayload={}", proposalPayload);
                            resultHandler.handleResult();
                        } else {
                            final String msg = "Adding of proposalPayload to P2P network failed.\n" +
                                    "proposalPayload=" + proposalPayload;
                            log.error(msg);
                            errorMessageHandler.handleErrorMessage(msg);
                        }
                    } else {
                        final String msg = "We received a different tx ID as we had in our proposal. " +
                                "That might be a caused due tx malleability. " +
                                "proposal.getTx().getHashAsString()=" + proposalTx.getHashAsString() +
                                ", transaction.getHashAsString()=" + txId;
                        log.error(msg);
                        errorMessageHandler.handleErrorMessage(msg);
                    }
                } catch (Throwable t) {
                    errorMessageHandler.handleErrorMessage(t.toString());
                }
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                errorMessageHandler.handleErrorMessage(t.toString());
            }
        });*/
    }

    public boolean removeProposal(Proposal proposal) {
        final ProposalPayload proposalPayload = proposal.getProposalPayload();
        // We allow removal which are not confirmed yet or if it we are in the right phase
        if (isMine(proposal)) {
            if (isInPhaseOrUnconfirmed(proposalPayload)) {
                boolean success = p2PService.removeData(proposalPayload, true);
                if (success)
                    removeProposalFromList(proposal);
                else
                    log.warn("Removal of proposal from p2p network failed. proposal={}", proposal);

                return success;
            } else {
                final String msg = "removeProposal called with a Proposal which is outside of the Proposal phase.";
                DevEnv.logErrorAndThrowIfDevMode(msg);
                return false;
            }
        } else {
            final String msg = "removeProposal called for a Proposal which is not ours. That must not happen.";
            DevEnv.logErrorAndThrowIfDevMode(msg);
            return false;
        }
    }

    public void persist() {
        storage.queueUpForSave();
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

    private void addProposal(ProposalPayload proposalPayload, boolean storeLocally) {
        if (!listContains(proposalPayload)) {
            log.info("We got added a ProposalPayload from P2P network.\nProposalPayload={}" + proposalPayload);
            observableList.add(createSpecificProposal(proposalPayload));

            if (storeLocally)
                persist();

            upDatePredicate();
        } else {
            if (!isMine(proposalPayload))
                log.warn("We already have an item with the same Proposal.");
        }
    }

    private boolean addToP2PNetwork(ProposalPayload proposalPayload) {
        return p2PService.addProtectedStorageEntry(proposalPayload, true);
    }

    private void publishMyProposalsIfWellConnected() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false. Also better for production version
        // to avoid activity peaks at startup
        UserThread.runAfter(() -> {
            if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
                p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
                publishMyProposals();
            }
        }, 2);
    }

    private void publishMyProposals() {
        activeProposals.stream()
                .filter(this::isMine)
                .forEach(proposal -> {
                    if (addToP2PNetwork(proposal.getProposalPayload())) {
                        log.info("Added ProposalPayload to P2P network.\nProposalPayload={}" + proposal.getProposalPayload());
                    } else {
                        log.warn("Adding of ProposalPayload to P2P network failed.\nProposalPayload={}" + proposal.getProposalPayload());
                    }
                });
    }

    private boolean isInPhaseOrUnconfirmed(ProposalPayload payload) {
        return readableBsqBlockChain.getTxMap().get(payload.getTxId()) == null ||
                (periodService.isTxInPhase(payload.getTxId(), PeriodService.Phase.PROPOSAL) &&
                        periodService.isTxInCurrentCycle(payload.getTxId()));
    }

    private boolean isMine(ProposalPayload proposalPayload) {
        return signaturePubKey.equals(proposalPayload.getOwnerPubKey());
    }

    private Proposal createSpecificProposal(ProposalPayload proposalPayload) {
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

    private boolean listContains(ProposalPayload proposalPayload) {
        return findProposal(proposalPayload).isPresent();
    }

    private Optional<Proposal> findProposal(ProposalPayload proposalPayload) {
        return observableList.stream()
                .filter(e -> e.getProposalPayload().equals(proposalPayload))
                .findAny();
    }

    private void removeProposalFromList(Proposal proposal) {
        if (observableList.remove(proposal))
            persist();
        else
            log.warn("We called removeProposalFromList at a proposal which was not in our list");
    }
}

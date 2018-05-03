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

package bisq.core.dao.voting.proposal;

import bisq.core.dao.state.BlockListener;
import bisq.core.dao.state.ChainHeightListener;
import bisq.core.dao.state.ParseBlockChainListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalAppendOnlyPayload;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalAppendOnlyStorageService;
import bisq.core.dao.voting.proposal.storage.protectedstorage.ProposalPayload;
import bisq.core.dao.voting.proposal.storage.protectedstorage.ProposalStorageService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;

import bisq.common.app.DevEnv;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains preliminaryProposals and confirmedProposals.
 * Republishes preliminaryProposals to append-only data store when entering the blind vote phase.
 */
@Slf4j
public class ProposalService implements ChainHeightListener, HashMapChangedListener, AppendOnlyDataStoreListener,
        BlockListener, ParseBlockChainListener {
    public interface ListChangeListener {
        void onPreliminaryProposalsChanged(List<Proposal> list);

        void onConfirmedProposalsChanged(List<Proposal> list);
    }

    private final P2PService p2PService;
    private final PeriodService periodService;
    private final StateService stateService;
    private final ProposalValidator proposalValidator;

    private final List<ListChangeListener> listeners = new CopyOnWriteArrayList<>();

    // Proposals we receive in the proposal phase. They can be removed in that phase and that list must not be used for
    // consensus critical code.
    @Getter
    private final List<Proposal> preliminaryProposals = new ArrayList<>();

    // Proposals which got added to the append-only data store at the beginning of the blind vote phase.
    // They cannot be removed anymore. This list is used for consensus critical code.
    @Getter
    private final List<Proposal> confirmedProposals = new ArrayList<>();
    private boolean rePublishInCycleExecuted;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalService(P2PService p2PService,
                           PeriodService periodService,
                           ProposalAppendOnlyStorageService proposalAppendOnlyStorageService,
                           ProposalStorageService proposalStorageService,
                           AppendOnlyDataStoreService appendOnlyDataStoreService,
                           ProtectedDataStoreService protectedDataStoreService,
                           StateService stateService,
                           ProposalValidator proposalValidator) {
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.stateService = stateService;
        this.proposalValidator = proposalValidator;

        appendOnlyDataStoreService.addService(proposalAppendOnlyStorageService);
        protectedDataStoreService.addService(proposalStorageService);

        stateService.addParseBlockChainListener(this);
        stateService.addChainHeightListener(this);
        stateService.addBlockListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ParseBlockChainListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
        stateService.removeParseBlockChainListener(this);

        // We wait until we have parsed the blockchain to have consistent data for validation before we register
        // our p2p network listeners.
        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);

        // Fill the lists with the data we have collected in out stores.
        fillPreliminaryProposals();
        fillConfirmedProposals();

        // Now as we have set all p2p network data lets run the onChainHeightChanged again in case we are on a
        // relevant blockHeight.
        onChainHeightChanged(stateService.getChainHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ChainHeightListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onChainHeightChanged(int blockHeight) {
        // When we enter the  blind vote phase we re-publish all proposals we have received from the p2p network to the
        // append only data store.
        // From now on we will access proposals only from that data store.
        if (!rePublishInCycleExecuted && blockHeight >= periodService.getFirstBlockOfPhase(blockHeight, DaoPhase.Phase.BLIND_VOTE)) {
            rePublishInCycleExecuted = true;
            rePublishProposalsToAppendOnlyDataStore();

            // At republish we get filled our appendOnlyDataStore synchronously so we can use that to fill our confirmed list.
            fillConfirmedProposals();
        } else if (periodService.isFirstBlockInCycle()) {
            // Cycle has changed, we reset the list.
            fillConfirmedProposals();

            // We reset the flag to enable again republishing when entering next blind vote phase
            rePublishInCycleExecuted = false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BlockListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(Block block) {
        // We use the block listener here as we need to get the tx fully parsed when checking if the proposal is valid
        fillPreliminaryProposals();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry entry) {
        onAddedProtectedData(entry);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry entry) {
        onRemovedProtectedData(entry);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        onAddedAppendOnlyData(payload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
    }

    public void addListener(ListChangeListener listener) {
        listeners.add(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillPreliminaryProposals() {
        preliminaryProposals.clear();
        p2PService.getDataMap().values().forEach(this::onAddedProtectedData);
        listeners.forEach(l -> l.onPreliminaryProposalsChanged(preliminaryProposals));
    }

    private void fillConfirmedProposals() {
        confirmedProposals.clear();
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(this::onAddedAppendOnlyData);
        listeners.forEach(l -> l.onConfirmedProposalsChanged(confirmedProposals));
    }

    private void rePublishProposalsToAppendOnlyDataStore() {
        preliminaryProposals.stream()
                .filter(proposalValidator::isValidAndConfirmed)
                .map(ProposalAppendOnlyPayload::new)
                .forEach(appendOnlyPayload -> {
                    boolean success = p2PService.addPersistableNetworkPayload(appendOnlyPayload, true);
                    if (!success)
                        log.warn("rePublishProposalsToAppendOnlyDataStore failed for proposal " + appendOnlyPayload.getProposal());
                });
    }

    private void onAddedProtectedData(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            final Proposal proposal = ((ProposalPayload) protectedStoragePayload).getProposal();
            if (!ProposalUtils.containsProposal(proposal, preliminaryProposals)) {
                if (proposalValidator.isValidAndConfirmed(proposal)) {
                    log.info("We received a new proposal from the P2P network. Proposal.txId={}, blockHeight={}",
                            proposal.getTxId(), stateService.getChainHeight());
                    preliminaryProposals.add(proposal);
                    listeners.forEach(l -> l.onPreliminaryProposalsChanged(preliminaryProposals));
                } else {
                    log.warn("We received a invalid proposal from the P2P network. Proposal.txId={}, blockHeight={}",
                            proposal.getTxId(), stateService.getChainHeight());
                }
            } else {
                log.debug("We received a new proposal from the P2P network but we have it already in out list. " +
                        "We ignore that proposal. Proposal.txId={}", proposal.getTxId());
            }
        }
    }

    private void onRemovedProtectedData(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            final Proposal proposal = ((ProposalPayload) protectedStoragePayload).getProposal();
            ProposalUtils.findProposalInList(proposal, preliminaryProposals)
                    .filter(p -> {
                        if (ProposalUtils.canRemoveProposal(p, stateService, periodService)) {
                            return true;
                        } else {
                            final String msg = "onRemoved called for a proposal which is outside of the proposal phase. " +
                                    "This is invalid and we ignore the call.";
                            DevEnv.logErrorAndThrowIfDevMode(msg);
                            return false;
                        }
                    })
                    .ifPresent(p -> {
                        ProposalUtils.removeProposalFromList(proposal, preliminaryProposals);
                        listeners.forEach(l -> l.onPreliminaryProposalsChanged(preliminaryProposals));
                    });
        }
    }

    private void onAddedAppendOnlyData(PersistableNetworkPayload payload) {
        if (payload instanceof ProposalAppendOnlyPayload) {
            final Proposal proposal = ((ProposalAppendOnlyPayload) payload).getProposal();
            if (!ProposalUtils.containsProposal(proposal, confirmedProposals)) {
                if (proposalValidator.isValidAndConfirmed(proposal)) {
                    log.info("We received a new append-only proposal from the P2P network. Proposal.txId={}", proposal.getTxId());
                    confirmedProposals.add(proposal);
                    listeners.forEach(l -> l.onConfirmedProposalsChanged(confirmedProposals));
                } else {
                    log.warn("We received a invalid append-only proposal from the P2P network. Proposal={}", proposal);
                }
            } else {
                log.debug("We received a new append-only proposal from the P2P network but we have it already in out list. " +
                        "We ignore that proposal. Proposal.txId={}", proposal.getTxId());
            }
        }
    }
}

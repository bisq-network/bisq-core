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

package bisq.core.dao.voting.ballot.proposal;

import bisq.core.dao.state.ChainHeightListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ballot.proposal.storage.appendonly.ProposalAppendOnlyPayload;
import bisq.core.dao.voting.ballot.proposal.storage.appendonly.ProposalAppendOnlyStorageService;
import bisq.core.dao.voting.ballot.proposal.storage.protectedstorage.ProposalPayload;
import bisq.core.dao.voting.ballot.proposal.storage.protectedstorage.ProposalStorageService;

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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains preliminaryProposals and confirmedProposals. Republishes preliminaryProposals to append-only data store
 * when entering the blind vote phase.
 */
@Slf4j
public class ProposalService implements ChainHeightListener, HashMapChangedListener, AppendOnlyDataStoreListener {
    private final P2PService p2PService;
    private final PeriodService periodService;
    private final StateService stateService;
    private final ProposalValidator proposalValidator;

    // Proposals we receive in the proposal phase. They can be removed in that phase and that list must not be used for
    // consensus critical code.
    @Getter
    private final List<Proposal> preliminaryProposals = new ArrayList<>();

    // Proposals which got added to the append-only data store at the beginning of the blind vote phase.
    // They cannot be removed anymore. This list is used for consensus critical code.
    @Getter
    private final List<Proposal> confirmedProposals = new ArrayList<>();


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

        stateService.addChainHeightListener(this);
        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ChainHeightListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onChainHeightChanged(int blockHeight) {
        // When we enter the  blind vote phase we re-publish all proposals we have received from the p2p network to the
        // append only data store.
        // From now on we will access proposals only from that data store.
        if (periodService.getFirstBlockOfPhase(blockHeight, DaoPhase.Phase.BLIND_VOTE) == blockHeight) {
            rePublishProposalsToAppendOnlyDataStore();

            // At republish we get set out local map synchronously so we can use that to fill our confirmed list.
            fillConfirmedProposals();
        } else if (periodService.getFirstBlockOfPhase(blockHeight, DaoPhase.Phase.PROPOSAL) == blockHeight) {
            // Cycle has changed, we reset the lists.
            fillPreliminaryProposals();
            fillConfirmedProposals();
        }
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
        fillPreliminaryProposals();
        fillConfirmedProposals();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillPreliminaryProposals() {
        preliminaryProposals.clear();
        p2PService.getDataMap().values().forEach(this::onAddedProtectedData);
    }

    private void fillConfirmedProposals() {
        confirmedProposals.clear();
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(this::onAddedAppendOnlyData);
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
                if (proposalValidator.isValid(proposal, true)) {
                    log.info("We received a new proposal from the P2P network. Proposal.txId={}", proposal.getTxId());
                    preliminaryProposals.add(proposal);
                } else {
                    log.warn("We received a invalid proposal from the P2P network. Proposal={}", proposal);
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
                    .ifPresent(p -> ProposalUtils.removeProposalFromList(proposal, preliminaryProposals));
        }
    }

    private void onAddedAppendOnlyData(PersistableNetworkPayload payload) {
        if (payload instanceof ProposalAppendOnlyPayload) {
            final Proposal proposal = ((ProposalAppendOnlyPayload) payload).getProposal();
            if (!ProposalUtils.containsProposal(proposal, confirmedProposals)) {
                if (proposalValidator.isValidAndConfirmed(proposal)) {
                    log.info("We received a new append-only proposal from the P2P network. Proposal.txId={}", proposal.getTxId());
                    confirmedProposals.add(proposal);
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

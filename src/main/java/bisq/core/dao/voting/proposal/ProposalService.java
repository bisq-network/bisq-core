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

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains preliminaryProposals and confirmedProposals.
 * Republishes preliminaryProposals to append-only data store when entering the blind vote phase.
 */
@Slf4j
public class ProposalService implements HashMapChangedListener, AppendOnlyDataStoreListener,
        BlockListener, ParseBlockChainListener {
    public interface ListChangeListener {
        void onPreliminaryProposalsChanged(List<Proposal> list);

        void onConfirmedProposalsChanged(List<Proposal> list);
    }

    private final P2PService p2PService;
    private final PeriodService periodService;
    private final StateService stateService;
    private final ProposalValidator proposalValidator;

    // Proposals we receive in the proposal phase. They can be removed in that phase and that list must not be used for
    // consensus critical code.
    @Getter
    private final ObservableList<Proposal> protectedStoreList = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<Proposal> preliminaryList = new FilteredList<>(protectedStoreList);

    // Proposals which got added to the append-only data store at the beginning of the blind vote phase.
    // They cannot be removed anymore. This list is used for consensus critical code.
    @Getter
    private final ObservableList<ProposalAppendOnlyPayload> appendOnlyStoreList = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<ProposalAppendOnlyPayload> verifiedList = new FilteredList<>(appendOnlyStoreList);


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
        stateService.addBlockListener(this);

        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);

        preliminaryList.setPredicate(proposal -> {
            // We do not validate phase, cycle and confirmation yet.
            if (proposalValidator.areDataFieldsValid(proposal)) {
                log.debug("We received a new proposal from the P2P network. Proposal.txId={}, blockHeight={}",
                        proposal.getTxId(), stateService.getChainHeight());
                return true;
            } else {
                log.debug("We received a invalid proposal from the P2P network. Proposal.txId={}, blockHeight={}",
                        proposal.getTxId(), stateService.getChainHeight());
                return false;
            }
        });

        setPredicateForAppendOnlyPayload();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ParseBlockChainListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
        stateService.removeParseBlockChainListener(this);

        // Fill the lists with the data we have collected in out stores.
        fillListFromProtectedStore();
        fillListFromAppendOnlyDataStore();
    }



    ///////////////////////////////////////////////////////////////////////////////////////////
    // BlockListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(Block block) {
        // We need to do that after parsing the block!
        if (block.getHeight() == getTriggerHeight()) {
            publishToAppendOnlyDataStore(block.getHash());

            // We need to update out predicate as we might not get called the onAppendOnlyDataAdded methods in case
            // we have the data already.
            fillListFromAppendOnlyDataStore();
            // In case the list has not changed the filter predicate would not be applied,
            // so we trigger a re-evaluation.
            setPredicateForAppendOnlyPayload();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry entry) {
        onProtectedDataAdded(entry);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry entry) {
        onProtectedDataRemoved(entry);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        onAppendOnlyDataAdded(payload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        // Fill the lists with the data we have collected in out stores.
        fillListFromAppendOnlyDataStore();
        fillListFromProtectedStore();
    }

    private void fillListFromProtectedStore() {
        p2PService.getDataMap().values().forEach(this::onProtectedDataAdded);
    }

    private void fillListFromAppendOnlyDataStore() {
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(this::onAppendOnlyDataAdded);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We need to trigger a change when our block height has changes by applying the predicate again.
    // The underlying list might not have been changes and would not trigger a new evaluation of the filtered list.
    private void setPredicateForAppendOnlyPayload() {
        verifiedList.setPredicate(proposalAppendOnlyPayload -> {
            if (!proposalValidator.isAppendOnlyPayloadValid(proposalAppendOnlyPayload,
                    getTriggerHeight(), stateService)) {
                log.debug("We received an invalid proposalAppendOnlyPayload. payload={}, blockHeight={}",
                        proposalAppendOnlyPayload, stateService.getChainHeight());
                return false;
            }

            if (proposalValidator.isValidAndConfirmed(proposalAppendOnlyPayload.getProposal())) {
                return true;
            } else {
                log.debug("We received a invalid append-only proposal from the P2P network. " +
                                "Proposal.txId={}, blockHeight={}",
                        proposalAppendOnlyPayload.getProposal().getTxId(), stateService.getChainHeight());
                return false;
            }
        });
    }

    // The blockHeight when we do the publishing of the proposals to the append only data store
    private int getTriggerHeight() {
        // TODO we will use 10 block after first block of break1 phase to avoid re-org issues.
        return periodService.getFirstBlockOfPhase(stateService.getChainHeight(), DaoPhase.Phase.BREAK1) /* + 10*/;
    }

    private void publishToAppendOnlyDataStore(String blockHash) {
        preliminaryList.stream()
                .filter(proposalValidator::isValidAndConfirmed)
                .map(proposal -> new ProposalAppendOnlyPayload(proposal, blockHash))
                .forEach(appendOnlyPayload -> {
                    boolean success = p2PService.addPersistableNetworkPayload(appendOnlyPayload, true);
                    if (!success)
                        log.warn("publishToAppendOnlyDataStore failed for proposal " + appendOnlyPayload.getProposal());
                });
    }

    private void onProtectedDataAdded(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            final Proposal proposal = ((ProposalPayload) protectedStoragePayload).getProposal();
            if (!protectedStoreList.contains(proposal))
                protectedStoreList.add(proposal);
        }
    }

    private void onProtectedDataRemoved(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            final Proposal proposal = ((ProposalPayload) protectedStoragePayload).getProposal();
            if (protectedStoreList.contains(proposal))
                protectedStoreList.remove(proposal);
        }
    }

    private void onAppendOnlyDataAdded(PersistableNetworkPayload persistableNetworkPayload) {
        if (persistableNetworkPayload instanceof ProposalAppendOnlyPayload) {
            ProposalAppendOnlyPayload proposalAppendOnlyPayload = (ProposalAppendOnlyPayload) persistableNetworkPayload;
            if (!appendOnlyStoreList.contains(proposalAppendOnlyPayload))
                appendOnlyStoreList.add(proposalAppendOnlyPayload);
        }
    }
}

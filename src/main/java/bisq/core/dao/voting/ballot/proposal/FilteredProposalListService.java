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
import bisq.core.dao.state.period.PeriodService;

import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides filtered observableLists of the Proposals from ProposalListService.
 */
@Slf4j
public class FilteredProposalListService implements ChainHeightListener, HashMapChangedListener, AppendOnlyDataStoreListener {
    private final ProposalService proposalService;
    private final P2PDataStorage p2pDataStorage;
    private final PeriodService periodService;
    @Getter
    private final ObservableList<Proposal> activeOrMyUnconfirmedProposals = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<Proposal> closedProposals = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FilteredProposalListService(ProposalService proposalService,
                                       P2PDataStorage p2pDataStorage,
                                       PeriodService periodService,
                                       StateService stateService) {
        this.proposalService = proposalService;
        this.p2pDataStorage = p2pDataStorage;
        this.periodService = periodService;

        stateService.addChainHeightListener(this);
        p2pDataStorage.addHashMapChangedListener(this);
        p2pDataStorage.addAppendOnlyDataStoreListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ChainHeightListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onChainHeightChanged(int blockHeight) {
        updateLists();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry data) {
        updateLists();
    }

    @Override
    public void onRemoved(ProtectedStorageEntry data) {
        updateLists();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        updateLists();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        updateLists();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateLists() {
        Set<Proposal> set = new HashSet<>(proposalService.getPreliminaryProposals());
        set.addAll(proposalService.getConfirmedProposals());
        activeOrMyUnconfirmedProposals.clear();
        activeOrMyUnconfirmedProposals.addAll(set);

        closedProposals.clear();
        closedProposals.addAll(ProposalUtils.getProposalsFromAppendOnlyStore(p2pDataStorage).stream()
                .filter(proposal -> periodService.isTxInPastCycle(proposal.getTxId(), periodService.getChainHeight()))
                .collect(Collectors.toList()));
    }
}

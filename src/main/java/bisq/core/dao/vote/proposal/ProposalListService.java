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

import bisq.core.dao.node.NodeExecutor;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.TxBlock;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.vote.PeriodService;

import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides filtered proposal collections to user thread clients.
 * It gets the required data from the sources running in the parser thread and will
 * copy it locally and map to user thread so the accessing clients don't have to deal
 * with threading.
 */
@Slf4j
public class ProposalListService {
    private final NodeExecutor nodeExecutor;
    private final ProposalService proposalService;
    private MyProposalService myProposalService;
    private final P2PDataStorage p2pDataStorage;
    private final PeriodService periodService;
    private final StateService stateService;

    @Getter
    private final ObservableList<Proposal> activeOrMyUnconfirmedProposals = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<Proposal> closedProposals = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalListService(NodeExecutor nodeExecutor,
                               ProposalService proposalService,
                               MyProposalService myProposalService,
                               P2PDataStorage p2pDataStorage,
                               PeriodService periodService,
                               StateService stateService) {
        this.nodeExecutor = nodeExecutor;
        this.proposalService = proposalService;
        this.myProposalService = myProposalService;
        this.p2pDataStorage = p2pDataStorage;
        this.periodService = periodService;
        this.stateService = stateService;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        stateService.addListener(new StateService.Listener() {
            // We set the nodeExecutor as we want to get called in the context of the parser thread
            @Override
            public Executor getExecutor() {
                return nodeExecutor.get();
            }

            @Override
            public void onBlockAdded(TxBlock txBlock) {
                updateLists(txBlock.getHeight());
            }
        });

        p2pDataStorage.addHashMapChangedListener(new HashMapChangedListener() { // User thread context
            // We set the nodeExecutor as we want to get called in the context of the parser thread
            @Override
            public Executor getExecutor() {
                return nodeExecutor.get();
            }

            @Override
            public void onAdded(ProtectedStorageEntry entry) {
                onProposalsChangeFromP2PNetwork(entry);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry entry) {
                onProposalsChangeFromP2PNetwork(entry);
            }
        });
    }

    public void shutDown() {
    }


    private void onProposalsChangeFromP2PNetwork(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload)
            updateLists(stateService.getChainHeadHeight());
    }

    private void updateLists(int chainHeadHeight) {
        // We are on the parser thread, so we are in sync with proposalService and stateService
        // proposalService.isMine and  proposalService.isValid are not accessing any mutable state.
        final List<Proposal> proposals = new ArrayList<>(proposalService.getProposals());
        Map<String, Optional<Tx>> map = new HashMap<>();
        proposals.forEach(proposal -> map.put(proposal.getTxId(), stateService.getTx(proposal.getTxId())));

        UserThread.execute(() -> {
            activeOrMyUnconfirmedProposals.clear();
            activeOrMyUnconfirmedProposals.addAll(proposals.stream()
                    .filter(proposal -> {
                        final Optional<Tx> optionalTx = map.get(proposal.getTxId());
                        final ProposalPayload proposalPayload = proposal.getProposalPayload();
                        return (optionalTx.isPresent() &&
                                proposalService.isValid(optionalTx.get(), proposalPayload) &&
                                periodService.isTxInCorrectCycle(optionalTx.get().getBlockHeight(), chainHeadHeight));
                    }).collect(Collectors.toList()));

            // We access myProposalService from user thread!
            final List<Proposal> myUnconfirmedProposals = myProposalService.getObservableList().stream()
                    .filter(proposal -> {
                        return (proposalService.isUnconfirmed(proposal.getTxId()));
                    }).collect(Collectors.toList());

            activeOrMyUnconfirmedProposals.addAll(myUnconfirmedProposals);

            closedProposals.clear();
            closedProposals.addAll(proposals.stream()
                    .filter(proposal -> {
                        final Optional<Tx> optionalTx = map.get(proposal.getTxId());
                        return optionalTx.isPresent() &&
                                proposalService.isValid(optionalTx.get(), proposal.getProposalPayload()) &&
                                periodService.isTxInPastCycle(optionalTx.get(), chainHeadHeight);
                    }).collect(Collectors.toList()));
        });
    }
}

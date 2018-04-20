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

package bisq.core.dao.consensus.vote.proposal;

import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.PeriodStateChangeListener;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;

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
    private final ProposalService proposalService;
    private final MyProposalService myProposalService;
    private final P2PDataStorage p2pDataStorage;
    private final PeriodService periodService;
    private final ProposalValidator proposalValidator;
    private final StateService stateService;

    @Getter
    private final ObservableList<Ballot> activeOrMyUnconfirmedBallots = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<Ballot> closedBallots = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalListService(ProposalService proposalService,
                               MyProposalService myProposalService,
                               P2PDataStorage p2pDataStorage,
                               PeriodService periodService,
                               ProposalValidator proposalValidator,
                               StateService stateService) {
        this.proposalService = proposalService;
        this.myProposalService = myProposalService;
        this.p2pDataStorage = p2pDataStorage;
        this.periodService = periodService;
        this.proposalValidator = proposalValidator;
        this.stateService = stateService;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        //TODO
        periodService.addPeriodStateChangeListener(new PeriodStateChangeListener() {
            @Override
            public boolean executeOnUserThread() {
                return false;
            }

            @Override
            public void onChainHeightChanged(int chainHeight) {
                updateLists(chainHeight);
            }
        });

        p2pDataStorage.addHashMapChangedListener(new HashMapChangedListener() {
            @Override
            public boolean executeOnUserThread() {
                return false;
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


    private void onProposalsChangeFromP2PNetwork(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof Proposal)
            updateLists(periodService.getChainHeight());
    }

    private void updateLists(int chainHeadHeight) {
        // We are on the parser thread, so we are in sync with proposalService and stateService
        // proposalService.isMine and  proposalService.isValid are not accessing any mutable state.
        final List<Ballot> ballots = new ArrayList<>(proposalService.getOpenBallotList().getList());
        Map<String, Optional<Tx>> map = new HashMap<>();
        ballots.forEach(proposal -> map.put(proposal.getTxId(), stateService.getTx(proposal.getTxId())));

        UserThread.execute(() -> {
            activeOrMyUnconfirmedBallots.clear();
            activeOrMyUnconfirmedBallots.addAll(ballots.stream()
                    .filter(proposal -> {
                        final Optional<Tx> optionalTx = map.get(proposal.getTxId());
                        final Proposal proposalPayload = proposal.getProposal();
                        return (optionalTx.isPresent() &&
                                proposalValidator.isValid(proposalPayload) &&
                                periodService.isInPhase(optionalTx.get().getBlockHeight(), Phase.PROPOSAL) &&
                                periodService.isTxInCorrectCycle(optionalTx.get().getBlockHeight(), chainHeadHeight));
                    }).collect(Collectors.toList()));

            // We access myProposalService from user thread!
            final List<Ballot> myUnconfirmedBallots = myProposalService.getObservableList().stream()
                    .filter(proposal -> {
                        return (proposalService.isUnconfirmed(proposal.getTxId()));
                    }).collect(Collectors.toList());

            activeOrMyUnconfirmedBallots.addAll(myUnconfirmedBallots);

            closedBallots.clear();

            //TODO once we have the votes we will merge the proposalPayloads with vote to create a proposal
           /* Set<Proposal> proposalPayloads = stateService.getProposalPayloads();

            final List<Ballot> proposalList1 = proposalPayloads.stream()
                    .filter(proposal -> {
                        final Optional<Tx> optionalTx = map.get(proposal.getTxId());
                        return optionalTx.isPresent() &&
                                proposalService.isValid(optionalTx.get(), proposal.getProposal()) &&
                                periodService.isTxInPastCycle(optionalTx.get(), chainHeadHeight);
                    }).collect(Collectors.toList());*/

            //TODO we dont keep old ballots anymore
            final List<Ballot> ballotList = ballots.stream()
                    .filter(proposal -> {
                        final Optional<Tx> optionalTx = map.get(proposal.getTxId());
                        final Proposal proposalPayload = proposal.getProposal();
                        return optionalTx.isPresent() &&
                                proposalValidator.isValid(proposalPayload) &&
                                periodService.isInPhase(optionalTx.get().getBlockHeight(), Phase.PROPOSAL) &&
                                periodService.isTxInPastCycle(optionalTx.get().getId(), chainHeadHeight);
                    }).collect(Collectors.toList());
            closedBallots.addAll(ballotList);
        });
    }
}

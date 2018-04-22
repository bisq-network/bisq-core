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

package bisq.core.dao.consensus.ballot;

import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.proposal.ProposalPayload;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;

import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides filtered Ballot collections to user thread clients.
 */
@Slf4j
public class FilteredBallotListService {
    private final BallotListService ballotListService;
    private final MyBallotListService myBallotListService;
    private final PeriodService periodService;
    private final StateService stateService;

    @Getter
    private final ObservableList<Ballot> activeOrMyUnconfirmedBallots = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<Ballot> closedBallots = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FilteredBallotListService(BallotListService ballotListService,
                                     MyBallotListService myBallotListService,
                                     P2PDataStorage p2pDataStorage,
                                     PeriodService periodService,
                                     StateService stateService) {
        this.ballotListService = ballotListService;
        this.myBallotListService = myBallotListService;
        this.periodService = periodService;
        this.stateService = stateService;

        stateService.addBlockListener(block -> updateLists());

        p2pDataStorage.addHashMapChangedListener(new HashMapChangedListener() {
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        updateLists();
    }

    // TODO maintain list
    public void persist() {
        ballotListService.persist();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onProposalsChangeFromP2PNetwork(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload)
            updateLists();
    }

    private void updateLists() {
        activeOrMyUnconfirmedBallots.clear();
        activeOrMyUnconfirmedBallots.addAll(ballotListService.getBallotList().stream()
                .filter(this::isUnconfirmedOrInPhaseAndCycle)
                .collect(Collectors.toList()));


        // We add our own proposals in case it was not already added to ballotListService.getBallotList()
        // Afterwards we check phases and cycle again to filter for active proposals
        activeOrMyUnconfirmedBallots.addAll(myBallotListService.getMyObservableBallotList().stream()
                .filter(ballot -> !ballotListService.findProposalInBallotList(ballot.getProposal(), activeOrMyUnconfirmedBallots).isPresent())
                .filter(this::isUnconfirmedOrInPhaseAndCycle)
                .collect(Collectors.toList()));

        closedBallots.clear();


        closedBallots.addAll(ballotListService.getBallotList().getList().stream()
                .filter(ballot -> stateService.getTx(ballot.getTxId()).isPresent())
                .filter(ballot -> stateService.getTx(ballot.getTxId())
                        .filter(tx -> !periodService.isTxInCorrectCycle(tx.getBlockHeight(), periodService.getChainHeight()))
                        .isPresent())
                .collect(Collectors.toList()));
    }

    private boolean isUnconfirmedOrInPhaseAndCycle(Ballot ballot) {
        final Optional<Tx> optionalTx = stateService.getTx(ballot.getTxId());
        return !optionalTx.isPresent() || optionalTx
                .filter(ballotListService::isTxInPhaseAndCycle)
                .isPresent();
    }
}

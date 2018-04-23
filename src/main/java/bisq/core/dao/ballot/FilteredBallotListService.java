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

package bisq.core.dao.ballot;

import bisq.core.dao.period.PeriodService;
import bisq.core.dao.proposal.ProposalPayload;
import bisq.core.dao.proposal.ProposalValidator;
import bisq.core.dao.state.StateService;

import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides filtered collections of the ballots from BallotListService.
 */
@Slf4j
public class FilteredBallotListService {
    private final BallotListService ballotListService;
    private final PeriodService periodService;
    private final StateService stateService;
    private final ProposalValidator proposalValidator;

    @Getter
    private final ObservableList<Ballot> activeOrMyUnconfirmedBallots = FXCollections.observableArrayList();
    @Getter
    private final ObservableList<Ballot> closedBallots = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FilteredBallotListService(BallotListService ballotListService,
                                     P2PDataStorage p2pDataStorage,
                                     PeriodService periodService,
                                     StateService stateService,
                                     ProposalValidator proposalValidator) {
        this.ballotListService = ballotListService;
        this.periodService = periodService;
        this.stateService = stateService;
        this.proposalValidator = proposalValidator;

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

    // We delegate to ballotListService
    public void persist() {
        ballotListService.persist();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onProposalsChangeFromP2PNetwork(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        // Need a bit of delay as otherwise the handler might get called before the item got removed from the
        // lists (at least at  localhost/regtest there are race conditions, over real network it will be much slower anyway)
        if (protectedStoragePayload instanceof ProposalPayload)
            UserThread.runAfter(this::updateLists, 100, TimeUnit.MILLISECONDS);
    }

    private void updateLists() {
        activeOrMyUnconfirmedBallots.clear();
        activeOrMyUnconfirmedBallots.addAll(ballotListService.getBallotList().stream()
                .filter(ballot -> BallotUtils.isProposalValid(ballot.getProposal(), proposalValidator, stateService, periodService))
                .collect(Collectors.toList()));

        closedBallots.clear();
        closedBallots.addAll(ballotListService.getBallotList().getList().stream()
                .filter(ballot -> stateService.getTx(ballot.getTxId()).isPresent())
                .filter(ballot -> stateService.getTx(ballot.getTxId())
                        .filter(tx -> !periodService.isTxInCorrectCycle(tx.getBlockHeight(), periodService.getChainHeight()))
                        .isPresent())
                .collect(Collectors.toList()));
    }
}

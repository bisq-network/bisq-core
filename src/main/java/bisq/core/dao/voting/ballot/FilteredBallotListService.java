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

package bisq.core.dao.voting.ballot;

import bisq.core.dao.state.BlockListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.proposal.ProposalValidator;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalAppendOnlyPayload;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;

import bisq.common.UserThread;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides filtered observableLists of the ballots from BallotListService.
 */
@Slf4j
public class FilteredBallotListService implements AppendOnlyDataStoreListener, BlockListener {
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

        stateService.addBlockListener(this);
        p2pDataStorage.addAppendOnlyDataStoreListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        if (payload instanceof ProposalAppendOnlyPayload)
            UserThread.runAfter(this::updateLists, 100, TimeUnit.MILLISECONDS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BlockListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(Block block) {
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
        activeOrMyUnconfirmedBallots.clear();
        activeOrMyUnconfirmedBallots.addAll(ballotListService.getBallotList().stream()
                .filter(ballot -> proposalValidator.isValidOrUnconfirmed(ballot.getProposal()))
                .collect(Collectors.toList()));

        closedBallots.clear();
        closedBallots.addAll(ballotListService.getBallotList().getList().stream()
                .filter(ballot -> stateService.getTx(ballot.getProposalTxId()).isPresent())
                .filter(ballot -> stateService.getTx(ballot.getProposalTxId())
                        .filter(tx -> !periodService.isTxInCorrectCycle(tx.getBlockHeight(), stateService.getChainHeight()))
                        .isPresent())
                .collect(Collectors.toList()));
    }
}

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

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.proposal.ProposalValidator;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides filtered observableLists of the ballots from BallotListService.
 */
@Slf4j
public class FilteredBallotListService implements BallotListService.ListChangeListener {
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
                                     PeriodService periodService,
                                     StateService stateService,
                                     ProposalValidator proposalValidator) {
        this.periodService = periodService;
        this.stateService = stateService;
        this.proposalValidator = proposalValidator;

        ballotListService.addListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BallotListService.ListChangeListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onListChanged(List<Ballot> list) {
        activeOrMyUnconfirmedBallots.clear();
        activeOrMyUnconfirmedBallots.addAll(list.stream()
                .filter(ballot -> proposalValidator.isValidOrUnconfirmed(ballot.getProposal()))
                .collect(Collectors.toList()));

        closedBallots.clear();
        closedBallots.addAll(list.stream()
                .filter(ballot -> stateService.getTx(ballot.getProposalTxId()).isPresent())
                .filter(ballot -> stateService.getTx(ballot.getProposalTxId())
                        .filter(tx -> !periodService.isTxInCorrectCycle(tx.getBlockHeight(), stateService.getChainHeight()))
                        .isPresent())
                .collect(Collectors.toList()));
    }
}

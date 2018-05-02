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

import bisq.core.dao.state.period.PeriodService;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides filtered observableLists of the Proposals from ProposalListService.
 */
@Slf4j
public class FilteredProposalListService implements ProposalService.ListChangeListener {
    private final ProposalService proposalService;
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
                                       PeriodService periodService) {
        this.proposalService = proposalService;
        this.periodService = periodService;

        proposalService.addListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ProposalService.ListChangeListener
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public void onPreliminaryProposalsChanged(List<Proposal> preliminaryProposals) {
        fillActiveOrMyUnconfirmedProposals();
    }

    @Override
    public void onConfirmedProposalsChanged(List<Proposal> confirmedProposals) {
        fillActiveOrMyUnconfirmedProposals();

        closedProposals.clear();
        closedProposals.addAll(confirmedProposals.stream()
                .filter(proposal -> periodService.isTxInPastCycle(proposal.getTxId(), periodService.getChainHeight()))
                .collect(Collectors.toList()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillActiveOrMyUnconfirmedProposals() {
        Set<Proposal> set = new HashSet<>(proposalService.getPreliminaryProposals());
        set.addAll(proposalService.getConfirmedProposals());
        activeOrMyUnconfirmedProposals.clear();
        activeOrMyUnconfirmedProposals.addAll(set);
    }

}

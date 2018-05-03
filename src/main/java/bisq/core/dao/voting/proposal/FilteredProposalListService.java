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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.state.ParseBlockChainListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.PeriodService;

import org.bitcoinj.core.TransactionConfidence;

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
 * Provides filtered observableLists of the Proposals from proposalService.
 */
@Slf4j
public class FilteredProposalListService implements ParseBlockChainListener, ProposalService.ListChangeListener, MyProposalListService.Listener {
    private final ProposalService proposalService;
    private final MyProposalListService myProposalListService;
    private final StateService stateService;
    private final BsqWalletService bsqWalletService;
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
                                       MyProposalListService myProposalListService,
                                       StateService stateService,
                                       BsqWalletService bsqWalletService,
                                       PeriodService periodService) {
        this.proposalService = proposalService;
        this.myProposalListService = myProposalListService;
        this.stateService = stateService;
        this.bsqWalletService = bsqWalletService;
        this.periodService = periodService;

        stateService.addParseBlockChainListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ParseBlockChainListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
        proposalService.addListener(this);
        myProposalListService.addListener(this);
        fillActiveOrMyUnconfirmedProposals();
        fillClosedProposals();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MyProposalListService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onListChanged(List<Proposal> list) {
        fillActiveOrMyUnconfirmedProposals();
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
        fillClosedProposals();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillActiveOrMyUnconfirmedProposals() {
        Set<Proposal> set = new HashSet<>(proposalService.getPreliminaryProposals());
        set.addAll(proposalService.getConfirmedProposals());
        activeOrMyUnconfirmedProposals.clear();
        activeOrMyUnconfirmedProposals.addAll(set);

        // We want to show our own unconfirmed proposals. Unconfirmed proposals from other users are not included
        // in the list.
        // If a tx is not found in the stateService it can be that it is either unconfirmed or invalid.
        // To avoid inclusion of invalid txs we add a check for the confidence type from the bsqWalletService.
        Set<Proposal> myUnconfirmedProposals = myProposalListService.getList().stream()
                .filter(p -> !stateService.getTx(p.getTxId()).isPresent()) // Tx is still not in our bsq blocks
                .filter(p -> {
                    final TransactionConfidence confidenceForTxId = bsqWalletService.getConfidenceForTxId(p.getTxId());
                    return confidenceForTxId != null &&
                            confidenceForTxId.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
                })
                .collect(Collectors.toSet());
        activeOrMyUnconfirmedProposals.addAll(myUnconfirmedProposals);
    }

    private void fillClosedProposals() {
        closedProposals.clear();
        closedProposals.addAll(proposalService.getConfirmedProposals().stream()
                .filter(proposal -> periodService.isTxInPastCycle(proposal.getTxId(), periodService.getChainHeight()))
                .collect(Collectors.toList()));
    }

}

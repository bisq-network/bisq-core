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
import bisq.core.dao.state.BlockListener;
import bisq.core.dao.state.ChainHeightListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalAppendOnlyPayload;

import org.bitcoinj.core.TransactionConfidence;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.ArrayList;
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
public class FilteredProposalListService implements ChainHeightListener, BlockListener, MyProposalListService.Listener {
    private final ProposalService proposalService;
    private final StateService stateService;
    private final MyProposalListService myProposalListService;
    private final BsqWalletService bsqWalletService;
    private final ProposalValidator proposalValidator;
    private final PeriodService periodService;
    @Getter
    private final ObservableList<Proposal> allProposals = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<Proposal> activeOrMyUnconfirmedProposals = new FilteredList<>(allProposals);
    @Getter
    private final FilteredList<Proposal> closedProposals = new FilteredList<>(allProposals);
    private List<Proposal> myUnconfirmedProposals = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FilteredProposalListService(ProposalService proposalService,
                                       StateService stateService,
                                       MyProposalListService myProposalListService,
                                       BsqWalletService bsqWalletService,
                                       ProposalValidator proposalValidator,
                                       PeriodService periodService) {
        this.proposalService = proposalService;
        this.stateService = stateService;
        this.myProposalListService = myProposalListService;
        this.bsqWalletService = bsqWalletService;
        this.proposalValidator = proposalValidator;
        this.periodService = periodService;

        stateService.addChainHeightListener(this);
        stateService.addBlockListener(this);
        myProposalListService.addListener(this);

        proposalService.getProtectedStoreList().addListener((ListChangeListener<Proposal>) c -> {
            updateLists();
        });
        proposalService.getAppendOnlyStoreList().addListener((ListChangeListener<ProposalAppendOnlyPayload>) c -> {
            updateLists();
        });

        updatePredicates();
    }

    private void updatePredicates() {
        activeOrMyUnconfirmedProposals.setPredicate(proposal -> proposalValidator.isValidAndConfirmed(proposal) ||
                myUnconfirmedProposals.contains(proposal));
        closedProposals.setPredicate(proposal -> periodService.isTxInPastCycle(proposal.getTxId(), periodService.getChainHeight()));
    }

    // At new cycle we don't get a list change but we want to update our predicates
    @Override
    public void onChainHeightChanged(int blockHeight) {
        // updateLists();
    }

    @Override
    public void onBlockAdded(Block block) {
        updateLists();
    }

    @Override
    public void onListChanged(List<Proposal> list) {
        updateLists();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void updateLists() {
        final List<Proposal> tempProposals = proposalService.getProtectedStoreList();
        final Set<Proposal> verifiedProposals = proposalService.getAppendOnlyStoreList().stream()
                .map(ProposalAppendOnlyPayload::getProposal)
                .collect(Collectors.toSet());
        Set<Proposal> set = new HashSet<>(tempProposals);
        set.addAll(verifiedProposals);

        // We want to show our own unconfirmed proposals. Unconfirmed proposals from other users are not included
        // in the list.
        // If a tx is not found in the stateService it can be that it is either unconfirmed or invalid.
        // To avoid inclusion of invalid txs we add a check for the confidence type from the bsqWalletService.
        myUnconfirmedProposals.clear();
        myUnconfirmedProposals.addAll(myProposalListService.getList().stream()
                .filter(p -> !stateService.getTx(p.getTxId()).isPresent()) // Tx is still not in our bsq blocks
                .filter(p -> {
                    final TransactionConfidence confidenceForTxId = bsqWalletService.getConfidenceForTxId(p.getTxId());
                    return confidenceForTxId != null &&
                            confidenceForTxId.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
                })
                .collect(Collectors.toList()));
        set.addAll(myUnconfirmedProposals);

        allProposals.clear();
        allProposals.addAll(set);

        updatePredicates();
    }
}

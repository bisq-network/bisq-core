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
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalPayload;

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
public class FilteredProposalListService implements BsqStateListener, MyProposalListService.Listener {
    private final ProposalService proposalService;
    private final BsqStateService bsqStateService;
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
    private final List<Proposal> myUnconfirmedProposals = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FilteredProposalListService(ProposalService proposalService,
                                       BsqStateService bsqStateService,
                                       MyProposalListService myProposalListService,
                                       BsqWalletService bsqWalletService,
                                       ProposalValidator proposalValidator,
                                       PeriodService periodService) {
        this.proposalService = proposalService;
        this.bsqStateService = bsqStateService;
        this.myProposalListService = myProposalListService;
        this.bsqWalletService = bsqWalletService;
        this.proposalValidator = proposalValidator;
        this.periodService = periodService;

        bsqStateService.addBsqStateListener(this);
        myProposalListService.addListener(this);

        proposalService.getProtectedStoreList().addListener((ListChangeListener<Proposal>) c -> {
            updateLists();
        });
        proposalService.getAppendOnlyStoreList().addListener((ListChangeListener<ProposalPayload>) c -> {
            updateLists();
        });

        updatePredicates();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    // At new cycle we don't get a list change but we want to update our predicates
    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onEmptyBlockAdded(Block block) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        updateLists();
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MyProposalListService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onListChanged(List<Proposal> list) {
        updateLists();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updatePredicates() {
        activeOrMyUnconfirmedProposals.setPredicate(proposal -> proposalValidator.isValidAndConfirmed(proposal) ||
                myUnconfirmedProposals.contains(proposal));
        closedProposals.setPredicate(proposal -> periodService.isTxInPastCycle(proposal.getTxId(), periodService.getChainHeight()));
    }

    private void updateLists() {
        final List<Proposal> tempProposals = proposalService.getProtectedStoreList();
        final Set<Proposal> verifiedProposals = proposalService.getAppendOnlyStoreList().stream()
                .map(ProposalPayload::getProposal)
                .collect(Collectors.toSet());
        Set<Proposal> set = new HashSet<>(tempProposals);
        set.addAll(verifiedProposals);

        // We want to show our own unconfirmed proposals. Unconfirmed proposals from other users are not included
        // in the list.
        // If a tx is not found in the bsqStateService it can be that it is either unconfirmed or invalid.
        // To avoid inclusion of invalid txs we add a check for the confidence type from the bsqWalletService.
        myUnconfirmedProposals.clear();
        myUnconfirmedProposals.addAll(myProposalListService.getList().stream()
                .filter(p -> !bsqStateService.getTx(p.getTxId()).isPresent()) // Tx is still not in our bsq blocks
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

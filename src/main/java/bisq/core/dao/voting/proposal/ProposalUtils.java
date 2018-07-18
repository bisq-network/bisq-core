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

import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalPayload;

import bisq.network.p2p.storage.P2PDataStorage;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProposalUtils {
    public static boolean containsProposal(Proposal proposal, List<Proposal> proposals) {
        return findProposalInList(proposal, proposals).isPresent();
    }

    public static Optional<Proposal> findProposalInList(Proposal proposal, List<Proposal> proposals) {
        return proposals.stream()
                .filter(p -> p.equals(proposal))
                .findAny();
    }

    public static boolean canRemoveProposal(Proposal proposal, BsqStateService bsqStateService, PeriodService periodService) {
        final Optional<Tx> optionalProposalTx = bsqStateService.getTx(proposal.getTxId());
        return !optionalProposalTx.isPresent() || isTxInProposalPhaseAndCycle(optionalProposalTx.get(), periodService, bsqStateService);
    }

    public static boolean isTxInProposalPhaseAndCycle(Tx tx, PeriodService periodService, BsqStateService bsqStateService) {
        return periodService.isInPhase(tx.getBlockHeight(), DaoPhase.Phase.PROPOSAL) &&
                periodService.isTxInCorrectCycle(tx.getBlockHeight(), bsqStateService.getChainHeight());
    }

    public static void removeProposalFromList(Proposal proposal, List<Proposal> proposals) {
        findProposalInList(proposal, proposals).ifPresent(proposals::remove);
    }

    public static List<Proposal> getProposalsFromAppendOnlyStore(P2PDataStorage p2pDataStorage) {
        return p2pDataStorage.getAppendOnlyDataStoreMap().values().stream()
                .filter(persistableNetworkPayload -> persistableNetworkPayload instanceof ProposalPayload)
                .map(persistableNetworkPayload -> ((ProposalPayload) persistableNetworkPayload).getProposal())
                .collect(Collectors.toList());
    }
}

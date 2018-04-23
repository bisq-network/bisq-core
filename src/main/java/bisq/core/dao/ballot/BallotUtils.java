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
import bisq.core.dao.period.Phase;
import bisq.core.dao.proposal.Proposal;
import bisq.core.dao.proposal.ProposalValidator;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BallotUtils {
    public static boolean ballotListContainsProposal(Proposal proposal, List<Ballot> ballotList) {
        return findProposalInBallotList(proposal, ballotList).isPresent();
    }

    public static Optional<Ballot> findProposalInBallotList(Proposal proposal, List<Ballot> ballotList) {
        return ballotList.stream()
                .filter(ballot -> ballot.getProposal().equals(proposal))
                .findAny();
    }

    // If unconfirmed or in correct phase/cycle we remove it
    public static boolean canRemoveProposal(Proposal proposal, StateService stateService, PeriodService periodService) {
        final Optional<Tx> optionalProposalTx = stateService.getTx(proposal.getTxId());
        return !optionalProposalTx.isPresent() || isTxInProposalPhaseAndCycle(optionalProposalTx.get(), periodService);
    }

    public static boolean isTxInProposalPhaseAndCycle(Tx tx, PeriodService periodService) {
        return periodService.isInPhase(tx.getBlockHeight(), Phase.PROPOSAL) &&
                periodService.isTxInCorrectCycle(tx.getBlockHeight(), periodService.getChainHeight());
    }

    public static boolean isProposalValid(Proposal proposal, ProposalValidator proposalValidator,
                                          StateService stateService, PeriodService periodService) {
        if (!proposalValidator.isValid(proposal)) {
            log.warn("proposal is invalid. proposal={}", proposal);
            return false;
        }

        final String txId = proposal.getTxId();
        Optional<Tx> optionalTx = stateService.getTx(txId);
        int chainHeight = stateService.getChainHeight();
        final boolean isTxConfirmed = optionalTx.isPresent();
        if (isTxConfirmed) {
            final int txHeight = optionalTx.get().getBlockHeight();
            if (!periodService.isTxInCorrectCycle(txHeight, chainHeight)) {
                log.warn("Tx is not in current cycle. proposal={}", proposal);
                return false;
            }
            if (!periodService.isInPhase(txHeight, Phase.PROPOSAL)) {
                log.warn("Tx is not in PROPOSAL phase. proposal={}", proposal);
                return false;
            }
        } else {
            if (!periodService.isInPhase(chainHeight, Phase.PROPOSAL)) {
                log.warn("We received an unconfirmed tx and are not in PROPOSAL phase anymore. proposal={}", proposal);
                return false;
            }
        }
        return true;
    }
}

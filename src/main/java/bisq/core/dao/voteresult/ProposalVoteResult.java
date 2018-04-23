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

package bisq.core.dao.voteresult;

import bisq.core.dao.proposal.Proposal;

import lombok.Value;

@Value
public class ProposalVoteResult {
    private final Proposal proposal;
    private final long stakeOfAcceptedVotes;
    private final long stakeOfRejectedVotes;

    ProposalVoteResult(Proposal proposal, long stakeOfAcceptedVotes, long stakeOfRejectedVotes) {
        this.proposal = proposal;
        this.stakeOfAcceptedVotes = stakeOfAcceptedVotes;
        this.stakeOfRejectedVotes = stakeOfRejectedVotes;
    }
}

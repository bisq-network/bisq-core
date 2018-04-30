/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.voting.ballot;

import bisq.core.dao.voting.ballot.compensation.CompensationBallot;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.compensation.CompensationProposal;
import bisq.core.dao.voting.ballot.vote.Vote;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BallotFactory {
    public static Ballot getBallot(Proposal proposal) {
        return getBallot(proposal, null);
    }

    public static Ballot getBallot(Proposal proposal, Vote vote) {
        //TODO impl others
        if (proposal instanceof CompensationProposal)
            return new CompensationBallot(proposal, vote);
        /*else if (proposal instanceof GenericProposal)
            return new GenericBallot(proposal, vote);*/
        else
            throw new RuntimeException("No Ballot implemented for proposal " + proposal);
    }
}

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

package bisq.core.dao.ballot.compensation;

import bisq.core.dao.ballot.Ballot;
import bisq.core.dao.proposal.Proposal;
import bisq.core.dao.proposal.ProposalType;
import bisq.core.dao.proposal.compensation.CompensationProposal;
import bisq.core.dao.vote.Vote;

import io.bisq.generated.protobuffer.PB;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Locally persisted CompensationBallot data.
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public class CompensationBallot extends Ballot {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CompensationBallot(Proposal proposal) {
        super(proposal, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CompensationBallot(Proposal proposal,
                               @Nullable Vote vote) {
        super(proposal, vote);
    }

    @Override
    public PB.Ballot toProtoMessage() {
        return getBallotBuilder().setCompensationBallot(PB.CompensationBallot.newBuilder())
                .build();
    }

    public static CompensationBallot fromProto(PB.Ballot proto) {
        return new CompensationBallot(Proposal.fromProto(proto.getProposal()),
                proto.hasVote() ? Vote.fromProto(proto.getVote()) : null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getRequestedBsq() {
        return getCompensationPayload().getRequestedBsq();
    }

    public Address getAddress() throws AddressFormatException {
        return getCompensationPayload().getAddress();
    }

    private CompensationProposal getCompensationPayload() {
        return (CompensationProposal) proposal;
    }

    @Override
    public ProposalType getType() {
        return ProposalType.COMPENSATION_REQUEST;
    }

    @Override
    public String toString() {
        return "CompensationBallot{} " + super.toString();
    }
}

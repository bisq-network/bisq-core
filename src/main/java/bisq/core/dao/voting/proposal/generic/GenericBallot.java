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

package bisq.core.dao.voting.proposal.generic;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Generic proposal for anything not covered by specific proposals.
 */
//TODO impl
@Slf4j
@Value
public class GenericBallot {
/*
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public GenericBallot(Proposal payload) {
        super(payload, null, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GenericBallot(Proposal proposal,
                          @Nullable Vote vote,
                          @Nullable Map<String, String> extraDataMap) {
        super(proposal,
                vote,
                extraDataMap);
    }

    @Override
    public PB.Ballot toProtoMessage() {
        return getBallotBuilder().setGenericBallot(PB.GenericBallot.newBuilder())
                .build();
    }

    public static GenericBallot fromProto(PB.Ballot proto) {
        return new GenericBallot(Proposal.fromProto(proto.getProposal()),
                proto.hasVote() ? Vote.fromProto(proto.getVote()) : null,
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.GENERIC;
    }

    private GenericProposal getGenericProposalPayload() {
        return (GenericProposal) proposal;
    }*/
}


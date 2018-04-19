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

package bisq.core.dao.consensus.vote.proposal.generic;

import bisq.core.dao.consensus.state.events.payloads.GenericProposal;
import bisq.core.dao.consensus.state.events.payloads.Proposal;
import bisq.core.dao.consensus.vote.Vote;
import bisq.core.dao.consensus.vote.proposal.Ballot;
import bisq.core.dao.consensus.vote.proposal.ProposalType;

import io.bisq.generated.protobuffer.PB;

import org.springframework.util.CollectionUtils;

import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Generic proposal for anything not covered by specific proposals.
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public class GenericBallot extends Ballot {

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
    }
}

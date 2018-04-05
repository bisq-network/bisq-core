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

package bisq.core.dao.vote.proposal.generic;

import bisq.core.dao.vote.proposal.Proposal;
import bisq.core.dao.vote.proposal.ProposalPayload;
import bisq.core.dao.vote.proposal.ProposalType;
import bisq.core.dao.vote.result.VoteResult;

import io.bisq.generated.protobuffer.PB;

import org.springframework.util.CollectionUtils;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * Generic proposal for anything not covered by specific proposals.
 */
public class GenericProposal extends Proposal {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public GenericProposal(ProposalPayload payload) {
        super(payload, null, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GenericProposal(ProposalPayload proposalPayload,
                            @Nullable VoteResult voteResult,
                            @Nullable Map<String, String> extraDataMap) {
        super(proposalPayload,
                voteResult,
                extraDataMap);
    }

    @Override
    public PB.Proposal toProtoMessage() {
        return getProposalBuilder().setGenericProposal(PB.GenericProposal.newBuilder())
                .build();
    }

    public static GenericProposal fromProto(PB.Proposal proto) {
        return new GenericProposal(ProposalPayload.fromProto(proto.getProposalPayload()),
                proto.hasVoteResult() ? VoteResult.fromProto(proto.getVoteResult()) : null,
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.GENERIC;
    }
}

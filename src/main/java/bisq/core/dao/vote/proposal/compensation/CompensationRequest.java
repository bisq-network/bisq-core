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

package bisq.core.dao.vote.proposal.compensation;

import bisq.core.dao.vote.proposal.Proposal;
import bisq.core.dao.vote.proposal.ProposalPayload;
import bisq.core.dao.vote.proposal.ProposalType;
import bisq.core.dao.vote.result.VoteResult;

import io.bisq.generated.protobuffer.PB;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

import org.springframework.util.CollectionUtils;

import java.util.Map;

import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Locally persisted CompensationRequest data.
 */
@Getter
public class CompensationRequest extends Proposal {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CompensationRequest(ProposalPayload proposalPayload) {
        super(proposalPayload, null, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CompensationRequest(ProposalPayload proposalPayload,
                                @Nullable VoteResult voteResult,
                                @Nullable Map<String, String> extraDataMap) {
        super(proposalPayload,
                voteResult,
                extraDataMap);
    }

    @Override
    public PB.Proposal toProtoMessage() {
        return getProposalBuilder().setCompensationRequest(PB.CompensationRequest.newBuilder())
                .build();
    }

    public static CompensationRequest fromProto(PB.Proposal proto) {
        return new CompensationRequest(ProposalPayload.fromProto(proto.getProposalPayload()),
                proto.hasVoteResult() ? VoteResult.fromProto(proto.getVoteResult()) : null,
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getRequestedBsq() {
        return getCompensationRequestPayload().getRequestedBsq();
    }

    public Address getAddress() throws AddressFormatException {
        return getCompensationRequestPayload().getAddress();
    }

    private CompensationRequestPayload getCompensationRequestPayload() {
        return (CompensationRequestPayload) proposalPayload;
    }

    @Override
    public ProposalType getType() {
        return ProposalType.COMPENSATION_REQUEST;
    }

    @Override
    public String toString() {
        return "CompensationRequest{} " + super.toString();
    }
}

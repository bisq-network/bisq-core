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

package bisq.core.dao.proposal;

import bisq.core.dao.proposal.compensation.CompensationRequest;
import bisq.core.dao.proposal.generic.GenericProposal;
import bisq.core.dao.vote.VoteResult;

import bisq.common.proto.ProtobufferException;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Base class for all proposals like compensation request, general purpose request, remove altcoin request, change fee request, etc.
 */
@Getter
public abstract class Proposal implements PersistablePayload {
    @Setter
    @Nullable
    protected VoteResult voteResult;
    @Setter
    protected boolean closed;
    protected final ProposalPayload proposalPayload;

    //TODO Remove. Fee should be resolved by block height of tx so not need to store it
    protected final long fee;
    @Setter
    protected Transaction tx;

    @Nullable
    protected Map<String, String> extraDataMap;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Proposal(ProposalPayload proposalPayload, long fee) {
        this(proposalPayload, fee, null, false, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected Proposal(ProposalPayload proposalPayload,
                       long fee,
                       @Nullable VoteResult voteResult,
                       boolean closed,
                       @Nullable Map<String, String> extraDataMap) {
        this.proposalPayload = proposalPayload;
        this.fee = fee;
        this.voteResult = voteResult;
        this.closed = closed;
        this.extraDataMap = extraDataMap;
    }

    @Override
    public PB.Proposal toProtoMessage() {
        return getProposalBuilder().build();
    }

    @NotNull
    protected PB.Proposal.Builder getProposalBuilder() {
        final PB.Proposal.Builder builder = PB.Proposal.newBuilder()
                .setProposalPayload(proposalPayload.getPayloadBuilder())
                .setFee(fee)
                .setClosed(closed);

        Optional.ofNullable(voteResult).ifPresent(e -> builder.setVoteResult((PB.VoteResult) e.toProtoMessage()));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder;
    }

    //TODO add other proposal types
    public static Proposal fromProto(PB.Proposal proto) {
        switch (proto.getMessageCase()) {
            case COMPENSATION_REQUEST:
                return CompensationRequest.fromProto(proto);
            case GENERIC_PROPOSAL:
                return GenericProposal.fromProto(proto);
            default:
                throw new ProtobufferException("Unknown message case: " + proto.getMessageCase());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getFeeAsCoin() {
        return Coin.valueOf(fee);
    }

    abstract public ProposalType getType();
}

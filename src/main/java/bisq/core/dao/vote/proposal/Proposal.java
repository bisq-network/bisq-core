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

package bisq.core.dao.vote.proposal;

import bisq.core.dao.vote.proposal.compensation.CompensationRequest;
import bisq.core.dao.vote.proposal.generic.GenericProposal;
import bisq.core.dao.vote.result.VoteResult;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import org.bitcoinj.core.Transaction;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Base class for all proposals like compensation request, generic request, remove altcoin request, change param request, etc.
 */
@Getter
public abstract class Proposal implements PersistablePayload {
    protected final ProposalPayload proposalPayload;
    @Nullable
    protected VoteResult voteResult;
    @Nullable
    protected Map<String, String> extraDataMap;

    // Not persisted!
    protected transient ObjectProperty<VoteResult> voteResultProperty = new SimpleObjectProperty<>();
    // Not persisted!
    @Nullable
    @Setter
    private transient Transaction tx;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Proposal(ProposalPayload proposalPayload) {
        this(proposalPayload, null, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected Proposal(ProposalPayload proposalPayload,
                       @Nullable VoteResult voteResult,
                       @Nullable Map<String, String> extraDataMap) {
        this.proposalPayload = proposalPayload;
        this.voteResult = voteResult;
        this.extraDataMap = extraDataMap;
    }

    @Override
    public PB.Proposal toProtoMessage() {
        return getProposalBuilder().build();
    }

    @NotNull
    protected PB.Proposal.Builder getProposalBuilder() {
        final PB.Proposal.Builder builder = PB.Proposal.newBuilder()
                .setProposalPayload(proposalPayload.getPayloadBuilder());

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
                throw new ProtobufferRuntimeException("Unknown message case: " + proto.getMessageCase());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setVoteResult(VoteResult voteResult) {
        this.voteResult = voteResult;
        voteResultProperty.set(voteResult);
    }

    abstract public ProposalType getType();

    public String getTxId() {
        return proposalPayload.getTxId();
    }

    public String getUid() {
        return proposalPayload.getUid();
    }


    @Override
    public String toString() {
        return "Proposal{" +
                "\n     proposalPayload=" + proposalPayload +
                ",\n     voteResult=" + voteResult +
                ",\n     extraDataMap=" + extraDataMap +
                ",\n     voteResultProperty=" + voteResultProperty +
                ",\n     tx=" + tx +
                "\n}";
    }
}

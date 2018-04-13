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

import bisq.core.dao.vote.Vote;
import bisq.core.dao.vote.proposal.compensation.CompensationRequest;
import bisq.core.dao.vote.proposal.generic.GenericProposal;

import bisq.common.proto.ProtobufferException;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Map;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Base class for all proposals like compensation request, generic request, remove altcoin request,
 * change param request, etc.
 * It contains the ProposalPayload and the Vote. If a ProposalPayload is ignored for voting the vote object is null.
 */
@Slf4j
@Getter
@EqualsAndHashCode
public abstract class Proposal implements PersistablePayload {
    protected final ProposalPayload proposalPayload;
    @Nullable
    protected Vote vote;
    @Nullable
    protected Map<String, String> extraDataMap;

    // Not persisted!
    protected transient ObjectProperty<Vote> voteResultProperty = new SimpleObjectProperty<>();


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
                       @Nullable Vote vote,
                       @Nullable Map<String, String> extraDataMap) {
        this.proposalPayload = proposalPayload;
        this.vote = vote;
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

        Optional.ofNullable(vote).ifPresent(e -> builder.setVote((PB.Vote) e.toProtoMessage()));
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

    public void setVote(Vote vote) {
        this.vote = vote;
        voteResultProperty.set(vote);
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
                ",\n     vote=" + vote +
                ",\n     extraDataMap=" + extraDataMap +
                "\n}";
    }
}

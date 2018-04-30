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
import bisq.core.dao.voting.proposal.ProposalType;
import bisq.core.dao.voting.ballot.vote.Vote;

import bisq.common.proto.ProtobufferException;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Base class for all ballots like compensation request, generic request, remove asset ballots and
 * change param ballots.
 * It contains the Proposal and the Vote. If a Proposal is ignored for voting the vote object is null.
 *
 * One proposal has about 278 bytes
 */
@Slf4j
@Getter
@EqualsAndHashCode
public abstract class Ballot implements PersistablePayload {

    public static Ballot createBallotFromProposal(Proposal proposal) {
        switch (proposal.getType()) {
            case COMPENSATION_REQUEST:
                return new CompensationBallot(proposal);
            case GENERIC:
                //TODO
                throw new RuntimeException("Not implemented yet");
            case CHANGE_PARAM:
                //TODO
                throw new RuntimeException("Not implemented yet");
            case REMOVE_ALTCOIN:
                //TODO
                throw new RuntimeException("Not implemented yet");
            default:
                final String msg = "Undefined ProposalType " + proposal.getType();
                log.error(msg);
                throw new RuntimeException(msg);
        }
    }

    protected final Proposal proposal;
    @Nullable
    protected Vote vote;

    // Not persisted!
    protected transient ObjectProperty<Vote> voteResultProperty = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Ballot(Proposal proposal) {
        this(proposal, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Ballot(Proposal proposal,
                  @Nullable Vote vote) {
        this.proposal = proposal;
        this.vote = vote;
    }

    @Override
    public PB.Ballot toProtoMessage() {
        return getBallotBuilder().build();
    }

    @NotNull
    protected PB.Ballot.Builder getBallotBuilder() {
        final PB.Ballot.Builder builder = PB.Ballot.newBuilder()
                .setProposal(proposal.getProposalBuilder());
        Optional.ofNullable(vote).ifPresent(e -> builder.setVote((PB.Vote) e.toProtoMessage()));
        return builder;
    }

    //TODO add other proposal types
    public static Ballot fromProto(PB.Ballot proto) {
        switch (proto.getMessageCase()) {
            case COMPENSATION_BALLOT:
                return CompensationBallot.fromProto(proto);
            /*case GENERIC_BALLOT:
                return GenericBallot.fromProto(proto);*/
            default:
                throw new ProtobufferException("Unknown message case: " + proto.getMessageCase());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setVote(@Nullable Vote vote) {
        this.vote = vote;
        voteResultProperty.set(vote);
    }

    abstract public ProposalType getType();

    public String getProposalTxId() {
        return proposal.getTxId();
    }

    public String getUid() {
        return proposal.getUid();
    }

    @Override
    public String toString() {
        return "Ballot{" +
                "\n     proposal=" + proposal +
                ",\n     vote=" + vote +
                "\n}";
    }
}

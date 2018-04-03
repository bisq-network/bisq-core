/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.vote.proposal;

import bisq.core.dao.vote.VoteConsensusCritical;

import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PersistableEnvelope wrapper for list of proposals. Used in vote consensus, so changes can break consensus!
 */
public class ProposalList extends PersistableList<Proposal> implements VoteConsensusCritical {

    public ProposalList(List<Proposal> list) {
        super(list);
    }

    public ProposalList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static ProposalList clone(ProposalList proposalList) throws InvalidProtocolBufferException {
        final PB.PersistableEnvelope proto = proposalList.toProtoMessage();
        return ProposalList.getProposalListFromBytes(proto.toByteArray());
    }

    public static ProposalList getProposalListFromBytes(byte[] bytes) throws InvalidProtocolBufferException {
        final PB.PersistableEnvelope envelope = PB.PersistableEnvelope.parseFrom(bytes);
        return ProposalList.fromProto(envelope.getProposalList());
    }

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setProposalList(getBuilder()).build();
    }

    public PB.ProposalList.Builder getBuilder() {
        return PB.ProposalList.newBuilder()
                .addAllProposal(getList().stream()
                        .map(Proposal::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static ProposalList fromProto(PB.ProposalList proto) {
        return new ProposalList(new ArrayList<>(proto.getProposalList().stream()
                .map(Proposal::fromProto)
                .collect(Collectors.toList())));
    }
}


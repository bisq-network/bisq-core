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

package bisq.core.dao.vote.votereveal;

import bisq.core.dao.vote.VoteConsensusCritical;
import bisq.core.dao.vote.blindvote.BlindVote;
import bisq.core.dao.vote.proposal.ProposalList;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import java.util.Optional;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@EqualsAndHashCode
@Slf4j
@Data
public class RevealedVote implements PersistablePayload, VoteConsensusCritical {

    private final ProposalList proposalList;
    private final BlindVote blindVote;
    @Nullable
    private String revealTxId;

    public RevealedVote(ProposalList proposalList, BlindVote blindVote) {
        this(proposalList, blindVote, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RevealedVote(ProposalList proposalList, BlindVote blindVote, @Nullable String revealTxId) {
        this.proposalList = proposalList;
        this.blindVote = blindVote;
        this.revealTxId = revealTxId;
    }

    @Override
    public PB.RevealedVote toProtoMessage() {
        final PB.RevealedVote.Builder builder = PB.RevealedVote.newBuilder()
                .setBlindVote(blindVote.getBuilder())
                .setProposalList(proposalList.getBuilder());
        Optional.ofNullable(revealTxId).ifPresent(builder::setRevealTxId);
        return builder.build();
    }

    public static RevealedVote fromProto(PB.RevealedVote proto) {
        return new RevealedVote(ProposalList.fromProto(proto.getProposalList()),
                BlindVote.fromProto(proto.getBlindVote()),
                proto.getRevealTxId().isEmpty() ? null : proto.getRevealTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getTxId() {
        return blindVote.getTxId();
    }

    public long getStake() {
        return blindVote.getStake();
    }
}

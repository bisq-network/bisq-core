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

package bisq.core.dao.vote.blindvote;

import bisq.core.dao.vote.VoteConsensusCritical;

import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BlindVoteList extends PersistableList<BlindVote> implements VoteConsensusCritical {

    public BlindVoteList(List<BlindVote> list) {
        super(list);
    }

    BlindVoteList() {
        super();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder()
                .setBlindVoteList(PB.BlindVoteList.newBuilder()
                        .addAllBlindVote(getList().stream()
                                .map(BlindVote::toBlindVote)
                                .collect(Collectors.toList())))
                .build();
    }

    public static PersistableEnvelope fromProto(PB.BlindVoteList proto) {
        return new BlindVoteList(new ArrayList<>(proto.getBlindVoteList().stream()
                .map(BlindVote::fromProto)
                .collect(Collectors.toList())));
    }
}

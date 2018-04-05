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

package bisq.core.dao.vote.result;

import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import lombok.ToString;

@ToString
public abstract class VoteResult implements PersistablePayload, NetworkPayload {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static VoteResult fromProto(PB.VoteResult proto) {
        switch (proto.getMessageCase()) {
            case BOOLEAN_VOTE_RESULT:
                return BooleanVoteResult.fromProto(proto);
            case LONG_VOTE_RESULT:
                return LongVoteResult.fromProto(proto);
            default:
                throw new ProtobufferException("Unknown message case: " + proto.getMessageCase());
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected PB.VoteResult.Builder getVoteResultBuilder() {
        return PB.VoteResult.newBuilder();
    }
}

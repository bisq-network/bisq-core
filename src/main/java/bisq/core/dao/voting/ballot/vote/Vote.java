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

package bisq.core.dao.voting.ballot.vote;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public abstract class Vote implements PersistablePayload, NetworkPayload {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Vote fromProto(PB.Vote proto) {
        switch (proto.getMessageCase()) {
            case BOOLEAN_VOTE:
                return BooleanVote.fromProto(proto);
            case LONG_VOTE:
                return LongVote.fromProto(proto);
            default:
                throw new ProtobufferRuntimeException("Unknown message case: " + proto.getMessageCase());
        }
    }

    @SuppressWarnings("WeakerAccess")
    public PB.Vote.Builder getVoteBuilder() {
        return PB.Vote.newBuilder();
    }
}

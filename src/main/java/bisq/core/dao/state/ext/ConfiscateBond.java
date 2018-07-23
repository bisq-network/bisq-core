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

package bisq.core.dao.state.ext;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import lombok.Value;

import javax.annotation.concurrent.Immutable;

@Immutable
@Value
public class ConfiscateBond implements PersistablePayload {
    private final byte[] hash;

    //TODO not used so far... is it needed?
    private final int activationHeight;

    public ConfiscateBond(byte[] hash, int activationHeight) {
        this.hash = hash;
        this.activationHeight = activationHeight;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public PB.ConfiscateBond toProtoMessage() {
        return PB.ConfiscateBond.newBuilder()
                .setHash(ByteString.copyFrom(hash))
                .setActivationHeight(activationHeight)
                .build();
    }

    public static ConfiscateBond fromProto(PB.ConfiscateBond proto) {
        return new ConfiscateBond(proto.getHash().toByteArray(),
                proto.getActivationHeight());
    }
}

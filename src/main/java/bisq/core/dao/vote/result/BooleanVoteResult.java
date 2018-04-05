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

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class BooleanVoteResult extends VoteResult {

    private boolean accepted;

    public BooleanVoteResult(boolean accepted) {
        this.accepted = accepted;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        return getVoteResultBuilder()
                .setBooleanVoteResult(PB.BooleanVoteResult.newBuilder()
                        .setAccepted(accepted))
                .build();
    }

    public static BooleanVoteResult fromProto(PB.VoteResult proto) {
        return new BooleanVoteResult(proto.getBooleanVoteResult().getAccepted());
    }
}

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

package bisq.core.dao.param;

import bisq.core.dao.vote.VoteConsensusCritical;

import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ParamChangeEventList extends PersistableList<ParamChangeEvent> implements VoteConsensusCritical {

    ParamChangeEventList() {
        super();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ParamChangeEventList(List<ParamChangeEvent> list) {
        super(list);
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder()
                .setParamChangeEventList(PB.ParamChangeEventList.newBuilder()
                        .addAllParamChangeEvent(getList().stream()
                                .map(ParamChangeEvent::toProtoMessage)
                                .collect(Collectors.toList())))
                .build();
    }

    public static PersistableEnvelope fromProto(PB.ParamChangeEventList proto) {
        return new ParamChangeEventList(new ArrayList<>(proto.getParamChangeEventList().stream()
                .map(ParamChangeEvent::fromProto)
                .collect(Collectors.toList())));
    }
}

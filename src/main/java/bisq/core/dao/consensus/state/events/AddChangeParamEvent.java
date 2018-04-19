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

package bisq.core.dao.consensus.state.events;

import bisq.core.dao.consensus.state.events.payloads.ChangeParamPayload;
import bisq.core.dao.consensus.vote.proposal.param.Param;

import io.bisq.generated.protobuffer.PB;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.concurrent.Immutable;

@Immutable
@EqualsAndHashCode(callSuper = true)
@Value
public class AddChangeParamEvent extends StateChangeEvent {

    public AddChangeParamEvent(ChangeParamPayload changeParamPayload, int height) {
        super(changeParamPayload, height);
    }

    public long getValue() {
        return getChangeParamPayload().getValue();
    }

    public Param getDaoParam() {
        return (getChangeParamPayload()).getParam();
    }

    public ChangeParamPayload getChangeParamPayload() {
        return (ChangeParamPayload) getPayload();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO add StateChangeEvent.builder
    @Override
    public PB.AddChangeParamEvent toProtoMessage() {
        final PB.AddChangeParamEvent.Builder builder = PB.AddChangeParamEvent.newBuilder()
                .setChangeParamPayload(getChangeParamPayload().toProtoMessage())
                .setHeight(getHeight());
        return builder.build();
    }

    public static AddChangeParamEvent fromProto(PB.AddChangeParamEvent proto) {
        return new AddChangeParamEvent(ChangeParamPayload.fromProto(proto.getChangeParamPayload()),
                proto.getHeight());
    }
}

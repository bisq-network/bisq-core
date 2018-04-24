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

package bisq.core.dao.state.events;

import bisq.core.dao.voting.proposal.param.Param;
import bisq.core.dao.voting.proposal.param.ParamChange;

import io.bisq.generated.protobuffer.PB;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.concurrent.Immutable;

@Immutable
@EqualsAndHashCode(callSuper = true)
@Value
public class ParamChangeEvent extends StateChangeEvent {

    public ParamChangeEvent(ParamChange paramChange, int height) {
        super(paramChange, height);
    }

    public long getValue() {
        return getParamChange().getValue();
    }

    public Param getDaoParam() {
        return (getParamChange()).getParam();
    }

    public ParamChange getParamChange() {
        return (ParamChange) getData();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO add StateChangeEvent.builder
    @Override
    public PB.ParamChangeEvent toProtoMessage() {
        final PB.ParamChangeEvent.Builder builder = PB.ParamChangeEvent.newBuilder()
                .setParamChange(getParamChange().toProtoMessage())
                .setHeight(getHeight());
        return builder.build();
    }

    public static ParamChangeEvent fromProto(PB.ParamChangeEvent proto) {
        return new ParamChangeEvent(ParamChange.fromProto(proto.getParamChange()),
                proto.getHeight());
    }
}

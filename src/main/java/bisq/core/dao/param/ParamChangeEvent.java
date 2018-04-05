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

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import lombok.Value;

@Value
public class ParamChangeEvent implements PersistablePayload {
    private final DaoParam daoParam;
    private final long value;
    private final int blockHeight;

    public ParamChangeEvent(DaoParam daoParam, long value, int blockHeight) {
        this.daoParam = daoParam;
        this.value = value;
        this.blockHeight = blockHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.ParamChangeEvent toProtoMessage() {
        final PB.ParamChangeEvent.Builder builder = PB.ParamChangeEvent.newBuilder()
                .setDaoParamOrdinal(daoParam.ordinal())
                .setValue(value)
                .setBlockHeight(blockHeight);
        return builder.build();
    }

    public static ParamChangeEvent fromProto(PB.ParamChangeEvent proto) {
        return new ParamChangeEvent(DaoParam.values()[proto.getDaoParamOrdinal()],
                proto.getValue(),
                proto.getBlockHeight());
    }


    @Override
    public String toString() {
        return "ParamChangeEvent{" +
                "\n     daoParam=" + daoParam +
                ",\n     value=" + value +
                ",\n     blockHeight=" + blockHeight +
                "\n}";
    }
}

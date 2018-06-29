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

package bisq.core.dao.state;

import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import io.bisq.generated.protobuffer.PB;

import lombok.Data;

@Data
public class MutableTxOutput {
    private final TxOutput txOutput;
    private TxOutputType txOutputType = TxOutputType.UNDEFINED;
    private boolean isUnspent = false;

    MutableTxOutput(TxOutput txOutput) {
        this.txOutput = txOutput;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MutableTxOutput(TxOutput txOutput, TxOutputType txOutputType, boolean isUnspent) {
        this.txOutput = txOutput;
        this.txOutputType = txOutputType;
        this.isUnspent = isUnspent;
    }

    public PB.MutableTxOutput toProtoMessage() {
        final PB.MutableTxOutput.Builder builder = PB.MutableTxOutput.newBuilder()
                .setTxOutput(txOutput.toProtoMessage())
                .setTxOutputType(txOutputType.toProtoMessage())
                .setIsUnspent(isUnspent);
        return builder.build();
    }

    public static MutableTxOutput fromProto(PB.MutableTxOutput proto) {
        return new MutableTxOutput(TxOutput.fromProto(proto.getTxOutput()),
                TxOutputType.fromProto(proto.getTxOutputType()),
                proto.getIsUnspent());
    }
}

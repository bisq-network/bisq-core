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

package bisq.core.dao.consensus.state.blockchain;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.concurrent.Immutable;

/**
 * An input is really just a reference to the spending output. It gets identified by the
 * txId and the index of that output. We use TxOutput.Key to encapsulate that.
 */
@Immutable
@Value
@EqualsAndHashCode
public class TxInput implements PersistablePayload {

    public static TxInput clone(TxInput txInput) {
        return new TxInput(txInput.getConnectedTxOutputTxId(),
                txInput.getConnectedTxOutputIndex());
    }

    private final String connectedTxOutputTxId;
    private final int connectedTxOutputIndex;

    public TxInput(String connectedTxOutputTxId, int connectedTxOutputIndex) {
        this.connectedTxOutputTxId = connectedTxOutputTxId;
        this.connectedTxOutputIndex = connectedTxOutputIndex;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.TxInput toProtoMessage() {
        final PB.TxInput.Builder builder = PB.TxInput.newBuilder()
                .setConnectedTxOutputTxId(connectedTxOutputTxId)
                .setConnectedTxOutputIndex(connectedTxOutputIndex);

        return builder.build();
    }

    public static TxInput fromProto(PB.TxInput proto) {
        return new TxInput(proto.getConnectedTxOutputTxId(),
                proto.getConnectedTxOutputIndex());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxOutput.Key getTxIdIndexTuple() {
        return new TxOutput.Key(connectedTxOutputTxId, connectedTxOutputIndex);
    }

    @Override
    public String toString() {
        return "TxInput{" +
                "\n     connectedTxOutputTxId='" + connectedTxOutputTxId + '\'' +
                ",\n     connectedTxOutputIndex=" + connectedTxOutputIndex +
                "\n}";
    }
}

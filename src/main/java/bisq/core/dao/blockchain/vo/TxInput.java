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

package bisq.core.dao.blockchain.vo;

import bisq.core.dao.blockchain.vo.util.TxIdIndexTuple;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

/**
 * An input is really just a reference to the spending output. It gets identified by the
 * txId and the index of the output. We use TxIdIndexTuple to encapsulate that.
 */
@Getter
@EqualsAndHashCode
public class TxInput implements PersistablePayload {

    public static TxInput clone(TxInput txInput, boolean reset) {
        return new TxInput(txInput.getConnectedTxOutputTxId(),
                txInput.getConnectedTxOutputIndex(),
                reset ? null : txInput.getConnectedTxOutput());
    }


    private final String connectedTxOutputTxId;
    private final int connectedTxOutputIndex;

    // Mutable data
    // We use a manual setter as we want to prevent that already set values get changed
    @Nullable
    private TxOutput connectedTxOutput;

    public TxInput(String connectedTxOutputTxId, int connectedTxOutputIndex) {
        this(connectedTxOutputTxId, connectedTxOutputIndex, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TxInput(String connectedTxOutputTxId, int connectedTxOutputIndex, @Nullable TxOutput connectedTxOutput) {
        this.connectedTxOutputTxId = connectedTxOutputTxId;
        this.connectedTxOutputIndex = connectedTxOutputIndex;
        this.connectedTxOutput = connectedTxOutput;
    }

    public PB.TxInput toProtoMessage() {
        final PB.TxInput.Builder builder = PB.TxInput.newBuilder()
                .setConnectedTxOutputTxId(connectedTxOutputTxId)
                .setConnectedTxOutputIndex(connectedTxOutputIndex);

        Optional.ofNullable(connectedTxOutput).ifPresent(e -> builder.setConnectedTxOutput(e.toProtoMessage()));

        return builder.build();
    }

    public static TxInput fromProto(PB.TxInput proto) {
        return new TxInput(proto.getConnectedTxOutputTxId(),
                proto.getConnectedTxOutputIndex(),
                proto.hasConnectedTxOutput() ? TxOutput.fromProto(proto.getConnectedTxOutput()) : null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxIdIndexTuple getTxIdIndexTuple() {
        return new TxIdIndexTuple(connectedTxOutputTxId, connectedTxOutputIndex);
    }

    public void setConnectedTxOutput(@Nullable TxOutput connectedTxOutput) {
        if (this.connectedTxOutput == null)
            this.connectedTxOutput = connectedTxOutput;
        else
            throw new IllegalStateException("Already set connectedTxOutput must not be changed.");
    }

    @Override
    public String toString() {
        return "TxInput{" +
                "\n     connectedTxOutputTxId='" + connectedTxOutputTxId + '\'' +
                ",\n     connectedTxOutputIndex=" + connectedTxOutputIndex +
                ",\n     connectedTxOutput=" + connectedTxOutput +
                "\n}";
    }
}

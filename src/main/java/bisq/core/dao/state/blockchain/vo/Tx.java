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

package bisq.core.dao.state.blockchain.vo;

import bisq.common.app.Version;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.stream.Collectors;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

@Immutable
@Slf4j
@Value
public class Tx implements PersistablePayload {

    public static Tx clone(Tx tx) {
        final ImmutableList<TxInput> inputs = ImmutableList.copyOf(tx.getInputs().stream()
                .map(TxInput::clone)
                .collect(Collectors.toList()));
        final ImmutableList<TxOutput> outputs = ImmutableList.copyOf(tx.getOutputs().stream()
                .map(TxOutput::clone)
                .collect(Collectors.toList()));
        return new Tx(tx.getTxVersion(),
                tx.getId(),
                tx.getBlockHeight(),
                tx.getBlockHash(),
                tx.getTime(),
                inputs,
                outputs);
    }

    private final String txVersion;
    private final String id;
    private final int blockHeight;
    private final String blockHash;
    private final long time;
    private final ImmutableList<TxInput> inputs;
    private final ImmutableList<TxOutput> outputs;

    public Tx(String id,
              int blockHeight,
              String blockHash,
              long time,
              ImmutableList<TxInput> inputs,
              ImmutableList<TxOutput> outputs) {
        this(Version.BSQ_TX_VERSION,
                id,
                blockHeight,
                blockHash,
                time,
                inputs,
                outputs);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Tx(String txVersion,
               String id,
               int blockHeight,
               String blockHash,
               long time,
               ImmutableList<TxInput> inputs,
               ImmutableList<TxOutput> outputs) {
        this.txVersion = txVersion;
        this.id = id;
        this.blockHeight = blockHeight;
        this.blockHash = blockHash;
        this.time = time;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public PB.Tx toProtoMessage() {
        final PB.Tx.Builder builder = PB.Tx.newBuilder()
                .setTxVersion(txVersion)
                .setId(id)
                .setBlockHeight(blockHeight)
                .setBlockHash(blockHash)
                .setTime(time)
                .addAllInputs(inputs.stream()
                        .map(TxInput::toProtoMessage)
                        .collect(Collectors.toList()))
                .addAllOutputs(outputs.stream()
                        .map(TxOutput::toProtoMessage)
                        .collect(Collectors.toList()));

        return builder.build();
    }

    public static Tx fromProto(PB.Tx proto) {
        return new Tx(proto.getTxVersion(),
                proto.getId(),
                proto.getBlockHeight(),
                proto.getBlockHash(),
                proto.getTime(),
                proto.getInputsList().isEmpty() ?
                        ImmutableList.copyOf(new ArrayList<>()) :
                        ImmutableList.copyOf(proto.getInputsList().stream()
                                .map(TxInput::fromProto)
                                .collect(Collectors.toList())),
                proto.getOutputsList().isEmpty() ?
                        ImmutableList.copyOf(new ArrayList<>()) :
                        ImmutableList.copyOf(proto.getOutputsList().stream()
                                .map(TxOutput::fromProto)
                                .collect(Collectors.toList())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxOutput getTxOutput(int index) {
        return outputs.get(index);
    }

    public TxOutput getLastOutput() {
        return getOutputs().get(getOutputs().size() - 1);
    }

    @Override
    public String toString() {
        return "Tx{" +
                "\n     txVersion='" + txVersion + '\'' +
                ",\n     id='" + id + '\'' +
                ",\n     blockHeight=" + blockHeight +
                ",\n     blockHash='" + blockHash + '\'' +
                ",\n     time=" + time +
                ",\n     inputs=" + inputs +
                ",\n     outputs=" + outputs +
                "\n}";
    }
}

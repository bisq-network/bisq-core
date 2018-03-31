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

import bisq.common.app.Version;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Getter
@ToString
@EqualsAndHashCode
@Slf4j
public class Tx implements PersistablePayload {

    public static Tx clone(Tx tx, boolean reset) {
        final ImmutableList<TxInput> inputs = ImmutableList.copyOf(tx.getInputs().stream()
                .map(txInput -> TxInput.clone(txInput, reset))
                .collect(Collectors.toList()));
        final ImmutableList<TxOutput> outputs = ImmutableList.copyOf(tx.getOutputs().stream()
                .map(txOutput -> TxOutput.clone(txOutput, reset))
                .collect(Collectors.toList()));
        return new Tx(tx.getTxVersion(),
                tx.getId(),
                tx.getBlockHeight(),
                tx.getBlockHash(),
                tx.getTime(),
                inputs,
                outputs,
                reset ? 0 : tx.getBurntFee(),
                reset ? TxType.UNDEFINED_TX_TYPE : tx.getTxType());
    }


    private final String txVersion;
    private final String id;
    private final int blockHeight;
    private final String blockHash;
    private final long time;
    private final ImmutableList<TxInput> inputs;
    private final ImmutableList<TxOutput> outputs;

    // Mutable data
    @Setter
    private long burntFee;
    @Setter
    @Nullable
    private TxType txType;

    public Tx(String id, int blockHeight,
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
                outputs,
                0,
                TxType.UNDEFINED_TX_TYPE);
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
               ImmutableList<TxOutput> outputs,
               long burntFee,
               @Nullable TxType txType) {
        this.txVersion = txVersion;
        this.id = id;
        this.blockHeight = blockHeight;
        this.blockHash = blockHash;
        this.time = time;
        this.inputs = inputs;
        this.outputs = outputs;
        this.burntFee = burntFee;
        this.txType = txType;
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
                        .collect(Collectors.toList()))
                .setBurntFee(burntFee);

        Optional.ofNullable(txType).ifPresent(e -> builder.setTxType(e.toProtoMessage()));

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
                                .collect(Collectors.toList())),
                proto.getBurntFee(),
                TxType.fromProto(proto.getTxType()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxOutput> getTxOutput(int index) {
        return outputs.size() > index ? Optional.of(outputs.get(index)) : Optional.empty();
    }
}

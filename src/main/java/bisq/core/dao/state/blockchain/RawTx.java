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

package bisq.core.dao.state.blockchain;

import bisq.common.app.Version;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.stream.Collectors;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * RawTx as we get it from the blockchain without BSQ specific data.
 */
@Immutable
@Slf4j
@Value
public final class RawTx implements PersistablePayload {

    public static RawTx clone(RawTx tx) {
        final ImmutableList<TxInput> txInputs = ImmutableList.copyOf(tx.getTxInputs().stream()
                .map(TxInput::clone)
                .collect(Collectors.toList()));
        final ImmutableList<RawTxOutput> rawTxOutputs = ImmutableList.copyOf(tx.getRawTxOutputs().stream()
                .map(RawTxOutput::clone)
                .collect(Collectors.toList()));
        return new RawTx(tx.getTxVersion(),
                tx.getId(),
                tx.getBlockHeight(),
                tx.getBlockHash(),
                tx.getTime(),
                txInputs,
                rawTxOutputs);
    }

    private final String txVersion;
    private final String id;
    private final int blockHeight;
    private final String blockHash;
    private final long time;
    private final ImmutableList<TxInput> txInputs;
    private final ImmutableList<RawTxOutput> rawTxOutputs;

    public RawTx(String id,
                 int blockHeight,
                 String blockHash,
                 long time,
                 ImmutableList<TxInput> txInputs,
                 ImmutableList<RawTxOutput> rawTxOutputs) {
        this(Version.BSQ_TX_VERSION,
                id,
                blockHeight,
                blockHash,
                time,
                txInputs,
                rawTxOutputs);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RawTx(String txVersion,
                  String id,
                  int blockHeight,
                  String blockHash,
                  long time,
                  ImmutableList<TxInput> txInputs,
                  ImmutableList<RawTxOutput> rawTxOutputs) {
        this.txVersion = txVersion;
        this.id = id;
        this.blockHeight = blockHeight;
        this.blockHash = blockHash;
        this.time = time;
        this.txInputs = txInputs;
        this.rawTxOutputs = rawTxOutputs;
    }

    public PB.RawTx toProtoMessage() {
        final PB.RawTx.Builder builder = PB.RawTx.newBuilder()
                .setTxVersion(txVersion)
                .setId(id)
                .setBlockHeight(blockHeight)
                .setBlockHash(blockHash)
                .setTime(time)
                .addAllTxInputs(txInputs.stream()
                        .map(TxInput::toProtoMessage)
                        .collect(Collectors.toList()))
                .addAllRawTxOutputs(rawTxOutputs.stream()
                        .map(RawTxOutput::toProtoMessage)
                        .collect(Collectors.toList()));

        return builder.build();
    }

    public static RawTx fromProto(PB.RawTx proto) {
        return new RawTx(proto.getTxVersion(),
                proto.getId(),
                proto.getBlockHeight(),
                proto.getBlockHash(),
                proto.getTime(),
                proto.getTxInputsList().isEmpty() ?
                        ImmutableList.copyOf(new ArrayList<>()) :
                        ImmutableList.copyOf(proto.getTxInputsList().stream()
                                .map(TxInput::fromProto)
                                .collect(Collectors.toList())),
                proto.getRawTxOutputsList().isEmpty() ?
                        ImmutableList.copyOf(new ArrayList<>()) :
                        ImmutableList.copyOf(proto.getRawTxOutputsList().stream()
                                .map(RawTxOutput::fromProto)
                                .collect(Collectors.toList())));
    }


    @Override
    public String toString() {
        return "RawTx{" +
                "\n     txVersion='" + txVersion + '\'' +
                ",\n     id='" + id + '\'' +
                ",\n     blockHeight=" + blockHeight +
                ",\n     blockHash='" + blockHash + '\'' +
                ",\n     time=" + time +
                ",\n     inputs=" + txInputs +
                ",\n     rawTxOutputs=" + rawTxOutputs +
                "\n}";
    }
}

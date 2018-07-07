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

import io.bisq.generated.protobuffer.PB;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.Getter;

import javax.annotation.Nullable;

@Data
public class MutableTx {
    private final Tx tx;

    @Nullable
    private TxType txType = null;

    // We don't use a list here as we don't know the order how the outputs gets added and if all gets added
    // key is the index of the output
    @Getter
    private Map<Integer, MutableTxOutput> mutableTxOutputMap = new HashMap<>();

    private long burntFee = 0L;
    // If not set it is -1. LockTime of 0 is a valid value.
    private int lockTime = -1;
    private int unlockBlockHeight = 0;

    public MutableTx(Tx tx) {
        this.tx = tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MutableTx(Tx tx,
                      @Nullable TxType txType,
                      Map<Integer, MutableTxOutput> mutableTxOutputMap,
                      long burntFee,
                      int lockTime,
                      int unlockBlockHeight) {
        this.tx = tx;
        this.txType = txType;
        this.mutableTxOutputMap = mutableTxOutputMap;
        this.burntFee = burntFee;
        this.lockTime = lockTime;
        this.unlockBlockHeight = unlockBlockHeight;
    }

    public PB.MutableTx toProtoMessage() {
        final PB.MutableTx.Builder builder = PB.MutableTx.newBuilder()
                .setTx(tx.toProtoMessage())
                .putAllMutableTxOutputs(mutableTxOutputMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toProtoMessage())))
                .setBurntFee(burntFee)
                .setLockTime(lockTime)
                .setUnlockBlockHeight(unlockBlockHeight);
        Optional.ofNullable(txType).ifPresent(txType -> builder.setTxType(txType.toProtoMessage()));
        return builder.build();
    }

    public static MutableTx fromProto(PB.MutableTx proto) {
        Map<Integer, MutableTxOutput> mutableTxOutputMap = proto.getMutableTxOutputsMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> MutableTxOutput.fromProto(e.getValue())));
        return new MutableTx(Tx.fromProto(proto.getTx()),
                TxType.fromProto(proto.getTxType()),
                mutableTxOutputMap,
                proto.getBurntFee(),
                proto.getLockTime(),
                proto.getUnlockBlockHeight());
    }
}

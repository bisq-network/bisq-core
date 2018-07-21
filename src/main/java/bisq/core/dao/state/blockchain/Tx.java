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

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.experimental.Delegate;

import javax.annotation.Nullable;

/**
 * Transaction containing BSQ specific data. The raw blockchain specific tx data are in the rawTx object.
 * We use lombok for convenience to delegate access to the data inside the rawTx.
 *
 * We are storing the txOutputs twice atm. We might optimize that to remove the rawTx and clone the fields here. But for
 * now we prefer ot have a less complex structure.
 */
@Data
public class Tx {
    public static Tx clone(Tx tx) {
        return new Tx(tx.getRawTx(),
                tx.getTxType(),
                tx.getTxOutputs(),
                tx.getBurntFee(),
                tx.getLockTime(),
                tx.getUnlockBlockHeight());
    }

    private interface ExcludesDelegateMethods<T> {
        PB.Tx toProtoMessage();
    }

    @Delegate(excludes = Tx.ExcludesDelegateMethods.class)
    private final RawTx rawTx;

    @Nullable
    private TxType txType = null;
    private ImmutableList<TxOutput> txOutputs;
    private long burntFee = 0L;
    // If not set it is -1. LockTime of 0 is a valid value.
    private int lockTime = -1;
    private int unlockBlockHeight = 0;

    public Tx(RawTx rawTx) {
        this.rawTx = rawTx;
        txOutputs = ImmutableList.copyOf(rawTx.getRawTxOutputs().stream().map(TxOutput::new).collect(Collectors.toList()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Tx(RawTx rawTx,
               @Nullable TxType txType,
               ImmutableList<TxOutput> txOutputs,
               long burntFee,
               int lockTime,
               int unlockBlockHeight) {
        this.rawTx = rawTx;
        this.txType = txType;
        this.txOutputs = txOutputs;
        this.burntFee = burntFee;
        this.lockTime = lockTime;
        this.unlockBlockHeight = unlockBlockHeight;
    }

    public PB.Tx toProtoMessage() {
        final PB.Tx.Builder builder = PB.Tx.newBuilder()
                .setTx(rawTx.toProtoMessage())
                .addAllTxOutputs(txOutputs.stream()
                        .map(TxOutput::toProtoMessage)
                        .collect(Collectors.toList()))
                .setBurntFee(burntFee)
                .setLockTime(lockTime)
                .setUnlockBlockHeight(unlockBlockHeight);
        Optional.ofNullable(txType).ifPresent(txType -> builder.setTxType(txType.toProtoMessage()));
        return builder.build();
    }

    public static Tx fromProto(PB.Tx proto) {
        return new Tx(RawTx.fromProto(proto.getTx()),
                TxType.fromProto(proto.getTxType()),
                proto.getTxOutputsList().isEmpty() ?
                        ImmutableList.copyOf(new ArrayList<>()) :
                        ImmutableList.copyOf(proto.getTxOutputsList().stream()
                                .map(TxOutput::fromProto)
                                .collect(Collectors.toList())),
                proto.getBurntFee(),
                proto.getLockTime(),
                proto.getUnlockBlockHeight());
    }

    public TxOutput getLastTxOutput() {
        return txOutputs.get(txOutputs.size() - 1);
    }
}

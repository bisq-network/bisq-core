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

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.stream.Collectors;

import lombok.Value;

@Value
public class BsqBlock implements PersistablePayload {

    public static BsqBlock clone(BsqBlock bsqBlock, boolean reset) {
        final ImmutableList<Tx> txs = ImmutableList.copyOf(bsqBlock.getTxs().stream()
                .map(tx -> Tx.clone(tx, reset))
                .collect(Collectors.toList()));
        return new BsqBlock(bsqBlock.getHeight(),
                bsqBlock.getTime(),
                bsqBlock.getHash(),
                bsqBlock.getPreviousBlockHash(),
                txs);
    }

    private final int height;
    private final long time;
    private final String hash;
    private final String previousBlockHash;
    private final ImmutableList<Tx> txs;

    public BsqBlock(int height, long time, String hash, String previousBlockHash, ImmutableList<Tx> txs) {
        this.height = height;
        this.time = time;
        this.hash = hash;
        this.previousBlockHash = previousBlockHash;
        this.txs = txs;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.BsqBlock toProtoMessage() {
        return PB.BsqBlock.newBuilder()
                .setHeight(height)
                .setTime(time)
                .setHash(hash)
                .setPreviousBlockHash(previousBlockHash)
                .addAllTxs(txs.stream()
                        .map(Tx::toProtoMessage)
                        .collect(Collectors.toList()))
                .build();
    }

    public static BsqBlock fromProto(PB.BsqBlock proto) {
        return new BsqBlock(proto.getHeight(),
                proto.getTime(),
                proto.getHash(),
                proto.getPreviousBlockHash(),
                proto.getTxsList().isEmpty() ?
                        ImmutableList.copyOf(new ArrayList<>()) :
                        ImmutableList.copyOf(proto.getTxsList().stream()
                                .map(Tx::fromProto)
                                .collect(Collectors.toList())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return "BsqBlock{" +
                "\n     height=" + height +
                ",\n     time=" + time +
                ",\n     hash='" + hash + '\'' +
                ",\n     previousBlockHash='" + previousBlockHash + '\'' +
                ",\n     txs=" + txs +
                "\n}";
    }
}

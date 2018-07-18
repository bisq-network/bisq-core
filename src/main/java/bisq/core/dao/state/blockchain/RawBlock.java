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

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.stream.Collectors;

import lombok.Value;

import javax.annotation.concurrent.Immutable;

/**
 * A block derived from the BTC blockchain and filtered for BSQ relevant transactions, though the transactions are not
 * verified at that stage. That block is passed to lite nodes over the P2P network. The validation is done by the lite
 * nodes themselves but the transactions are already filtered for BSQ only transactions to keep bandwidth requirements
 * low.
 */
@Immutable
@Value
public final class RawBlock implements PersistablePayload {

    public static RawBlock clone(RawBlock block) {
        final ImmutableList<RawTx> txs = ImmutableList.copyOf(block.getRawTxs().stream()
                .map(RawTx::clone)
                .collect(Collectors.toList()));
        return new RawBlock(block.getHeight(),
                block.getTime(),
                block.getHash(),
                block.getPreviousBlockHash(),
                txs);
    }

    public static RawBlock fromBlock(Block block) {
        ImmutableList<RawTx> txs = ImmutableList.copyOf(block.getTxs().stream().map(Tx::getRawTx).collect(Collectors.toList()));
        return new RawBlock(block.getHeight(),
                block.getTime(),
                block.getHash(),
                block.getPreviousBlockHash(),
                txs);
    }

    private final int height;
    private final long time; // in seconds!
    private final String hash;
    private final String previousBlockHash;
    private final ImmutableList<RawTx> rawTxs;

    public RawBlock(int height, long time, String hash, String previousBlockHash, ImmutableList<RawTx> rawTxs) {
        this.height = height;
        this.time = time;
        this.hash = hash;
        this.previousBlockHash = previousBlockHash;
        this.rawTxs = rawTxs;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.RawBlock toProtoMessage() {
        return PB.RawBlock.newBuilder()
                .setHeight(height)
                .setTime(time)
                .setHash(hash)
                .setPreviousBlockHash(previousBlockHash)
                .addAllRawTxs(rawTxs.stream()
                        .map(RawTx::toProtoMessage)
                        .collect(Collectors.toList()))
                .build();
    }

    public static RawBlock fromProto(PB.RawBlock proto) {
        return new RawBlock(proto.getHeight(),
                proto.getTime(),
                proto.getHash(),
                proto.getPreviousBlockHash(),
                proto.getRawTxsList().isEmpty() ?
                        ImmutableList.copyOf(new ArrayList<>()) :
                        ImmutableList.copyOf(proto.getRawTxsList().stream()
                                .map(RawTx::fromProto)
                                .collect(Collectors.toList())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return "RawBlock{" +
                "\n     height=" + height +
                ",\n     time=" + time +
                ",\n     hash='" + hash + '\'' +
                ",\n     previousBlockHash='" + previousBlockHash + '\'' +
                ",\n     rawTxs=" + rawTxs +
                "\n}";
    }
}

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
import java.util.List;
import java.util.stream.Collectors;

import lombok.Value;

/**
 * A block derived from the BTC blockchain and filtered for BSQ relevant transactions. The transactions are
 * verified and contain BSQ specific data. The transactions gets added at parsing.
 * We don't store the rawBlock here as we don't want to persist all the tx data twice (RawTx and Tx list).
 * A common super class could be used for reducing code duplications of the fields.
 */
@Value
public class Block implements PersistablePayload {

    public static Block clone(Block block) {
        final ImmutableList<Tx> txs = ImmutableList.copyOf(block.getTxs().stream()
                .map(Tx::clone)
                .collect(Collectors.toList()));
        return new Block(block.getHeight(),
                block.getTime(),
                block.getHash(),
                block.getPreviousBlockHash(),
                txs);
    }

    private final int height;
    private final long time; // in seconds!
    private final String hash;
    private final String previousBlockHash;
    private final List<Tx> txs;

    public Block(int height, long time, String hash, String previousBlockHash) {
        this(height, time, hash, previousBlockHash, new ArrayList<>());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Block(int height, long time, String hash, String previousBlockHash, List<Tx> txs) {
        this.height = height;
        this.time = time;
        this.hash = hash;
        this.previousBlockHash = previousBlockHash;
        this.txs = txs;
    }

    public PB.Block toProtoMessage() {
        return PB.Block.newBuilder()
                .setHeight(height)
                .setTime(time)
                .setHash(hash)
                .setPreviousBlockHash(previousBlockHash)
                .addAllTxs(txs.stream()
                        .map(Tx::toProtoMessage)
                        .collect(Collectors.toList()))
                .build();
    }

    public static Block fromProto(PB.Block proto) {
        return new Block(proto.getHeight(),
                proto.getTime(),
                proto.getHash(),
                proto.getPreviousBlockHash(),
                proto.getTxsList().isEmpty() ?
                        new ArrayList<>() :
                        new ArrayList<>(proto.getTxsList().stream()
                                .map(Tx::fromProto)
                                .collect(Collectors.toList())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return "Block{" +
                "\n     height=" + height +
                ",\n     time=" + time +
                ",\n     hash='" + hash + '\'' +
                ",\n     previousBlockHash='" + previousBlockHash + '\'' +
                ",\n     txs=" + txs +
                "\n}";
    }
}

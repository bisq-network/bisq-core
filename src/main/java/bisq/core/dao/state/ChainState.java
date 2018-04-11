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

package bisq.core.dao.state;

import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.util.TxIdIndexTuple;

import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


/**
 * Root class for the state of the crucial Bsq data.
 * We maintain 2 immutable data structures, the bsqBlocks and the stateChangeEvents.
 * All mutable data is kept in maps.
 * The complete ChainState data gets persisted as one file.
 */
@Slf4j
@Getter
public class ChainState implements PersistableEnvelope {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Immutable data structures
    // The blockchain data filtered for Bsq transactions
    private final LinkedList<BsqBlock> bsqBlocks;

    // The state change events containing any non-blockchain data which can trigger a state change in the Bisq DAO
    private final LinkedList<StateChangeEvent> stateChangeEvents;


    // Mutable data kept in maps
    private final Map<TxIdIndexTuple, TxOutput> unspentTxOutputs;
    @Setter
    private Tx genesisTx;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ChainState() {
        this(new LinkedList<>(),
                new LinkedList<>(),
                new HashMap<>());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ChainState(LinkedList<BsqBlock> bsqBlocks,
                       LinkedList<StateChangeEvent> stateChangeEvents,
                       Map<TxIdIndexTuple, TxOutput> unspentTxOutputs) {
        this.bsqBlocks = bsqBlocks;
        this.stateChangeEvents = stateChangeEvents;
        this.unspentTxOutputs = unspentTxOutputs;
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setBsqBlockChain(getBsqBlockChainBuilder()).build();
    }

    private PB.BsqBlockChain.Builder getBsqBlockChainBuilder() {
        final PB.BsqBlockChain.Builder builder = PB.BsqBlockChain.newBuilder();
        /*
        builder.addAllBsqBlocks(bsqBlocks.stream()
                        .map(BsqBlock::toProtoMessage)
                        .collect(Collectors.toList()))
                .putAllTxMap(txMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                v -> v.getValue().toProtoMessage())))
                .putAllUnspentTxOutputsMap(unspentTxOutputs.entrySet().stream()
                        .collect(Collectors.toMap(k -> k.getKey().getAsString(),
                                v -> v.getValue().toProtoMessage())))
                .setGenesisTxId(genesisTxId)
                .setGenesisBlockHeight(genesisBlockHeight)
                .setChainHeadHeight(chainHeadHeight);

        Optional.ofNullable(genesisTx).ifPresent(e -> builder.setGenesisTx(genesisTx.toProtoMessage()));*/

        return builder;
    }

    public static PersistableEnvelope fromProto(PB.BsqBlockChain proto) {
        return null;
        /*new ChainState(new LinkedList<>(proto.getBsqBlocksList().stream()
                .map(BsqBlock::fromProto)
                .collect(Collectors.toList())),
                new HashMap<>(proto.getTxMapMap().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, v -> Tx.fromProto(v.getValue())))),
                new HashMap<>(proto.getUnspentTxOutputsMapMap().entrySet().stream()
                        .collect(Collectors.toMap(k -> new TxIdIndexTuple(k.getKey()), v -> TxOutput.fromProto(v.getValue())))),
                proto.getGenesisTxId(),
                proto.getGenesisBlockHeight(),
                proto.getChainHeadHeight(),
                proto.hasGenesisTx() ? Tx.fromProto(proto.getGenesisTx()) : null)
        ;*/
    }

    public int getChainHeadHeight() {
        return !getBsqBlocks().isEmpty() ? getBsqBlocks().getLast().getHeight() : 0;
    }

    public ChainState getClone() {
        //TODO
        return null;
        // return lock.read(() -> (ChainStateService) ChainStateService.fromProto(chainStateService.getBsqBlockChainBuilder().build()));
    }

    public ChainState getClone(ChainState snapshotCandidate) {
        return null;
    }
}


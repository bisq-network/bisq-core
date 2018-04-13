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

import bisq.core.dao.state.blockchain.TxBlock;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.events.StateChangeEvent;
import bisq.core.dao.vote.proposal.ProposalPayload;

import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


/**
 * Root class of all relevant data for determining the state of the BSQ block chain.
 * We maintain 2 immutable data structures, the txBlocks and the stateChangeEvents.
 * All mutable data is kept in maps.
 */
@Slf4j
@Getter
public class State implements PersistableEnvelope {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Immutable data structures
    // The blockchain data filtered for Bsq transactions
    private final LinkedList<TxBlock> txBlocks;

    // The state change events containing any non-blockchain data which can trigger a state change in the Bisq DAO
    private final LinkedList<StateChangeEvent> stateChangeEvents;


    // Mutable data

    // Tx specific, key is txId
    private final Map<String, TxType> txTypeMap = new HashMap<>();
    private final Map<String, Long> burntFeeMap = new HashMap<>();
    private final Map<String, Integer> issuanceBlockHeightMap = new HashMap<>();

    // TxInput specific, key is txId
    private final Map<String, TxOutput> connectedTxOutputMap = new HashMap<>();

    // TxOutput specific
    private final Map<TxOutput.Key, TxOutput> unspentTxOutputMap;
    private final Map<TxOutput.Key, TxOutputType> txOutputTypeMap = new HashMap<>();
    private final Map<TxOutput.Key, SpentInfo> txOutputSpentInfoMap = new HashMap<>();

    // non blockchain data
    private final Map<String, ProposalPayload> proposalPayloadByTxIdMap = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public State() {
        this(new LinkedList<>(),
                new LinkedList<>(),
                new HashMap<>());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private State(LinkedList<TxBlock> txBlocks,
                  LinkedList<StateChangeEvent> stateChangeEvents,
                  Map<TxOutput.Key, TxOutput> unspentTxOutputMap) {
        this.txBlocks = txBlocks;
        this.stateChangeEvents = stateChangeEvents;
        this.unspentTxOutputMap = unspentTxOutputMap;

    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setBsqBlockChain(getBsqBlockChainBuilder()).build();
    }

    //TODO
    private PB.BsqBlockChain.Builder getBsqBlockChainBuilder() {
        final PB.BsqBlockChain.Builder builder = PB.BsqBlockChain.newBuilder();
        /*
        builder.addAllBsqBlocks(txBlocks.stream()
                        .map(TxBlock::toProtoMessage)
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

    //TODO
    public static PersistableEnvelope fromProto(PB.BsqBlockChain proto) {
        return null;
        /*new State(new LinkedList<>(proto.getBsqBlocksList().stream()
                .map(TxBlock::fromProto)
                .collect(Collectors.toList())),
                new HashMap<>(proto.getTxMapMap().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, v -> Tx.fromProto(v.getValue())))),
                new HashMap<>(proto.getUnspentTxOutputsMapMap().entrySet().stream()
                        .collect(Collectors.toMap(k -> new Key(k.getKey()), v -> TxOutput.fromProto(v.getValue())))),
                proto.getGenesisTxId(),
                proto.getGenesisBlockHeight(),
                proto.getChainHeadHeight(),
                proto.hasGenesisTx() ? Tx.fromProto(proto.getGenesisTx()) : null)
        ;*/
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int getChainHeadHeight() {
        return !this.getTxBlocks().isEmpty() ? this.getTxBlocks().getLast().getHeight() : 0;
    }

    public State getClone() {
        //TODO
        return this;
        // return lock.read(() -> (StateService) StateService.fromProto(stateService.getBsqBlockChainBuilder().build()));
    }

    //TODO
    public State getClone(State snapshotCandidate) {
        return this;
    }
}


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

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputKey;
import bisq.core.dao.state.ext.Issuance;
import bisq.core.dao.state.ext.ParamChange;
import bisq.core.dao.state.period.Cycle;

import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


/**
 * Root class for mutable state of the DAO.
 */
@Slf4j
public class State implements PersistableEnvelope {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final Coin GENESIS_TOTAL_SUPPLY = Coin.parseCoin("2.5");

    static Coin getGenesisTotalSupply() {
        return GENESIS_TOTAL_SUPPLY;
    }

    //TODO not sure if we will use that
    private static final int ISSUANCE_MATURITY = 144 * 30; // 30 days

    static int getIssuanceMaturity() {
        return ISSUANCE_MATURITY;
    }

    // mainnet
    // this tx has a lot of outputs
    // https://blockchain.info/de/tx/ee921650ab3f978881b8fe291e0c025e0da2b7dc684003d7a03d9649dfee2e15
    // BLOCK_HEIGHT 411779
    // 411812 has 693 recursions
    // block 376078 has 2843 recursions and caused once a StackOverflowError, a second run worked. Took 1,2 sec.

    public static String getDefaultGenesisTxId() {
        return DEFAULT_GENESIS_TX_ID;
    }

    public static int getDefaultGenesisBlockHeight() {
        return DEFAULT_GENESIS_BLOCK_HEIGHT;
    }

    // BTC MAIN NET
    // new: --genesisBlockHeight=524717 --genesisTxId=81855816eca165f17f0668898faa8724a105196e90ffc4993f4cac980176674e
    private static final String DEFAULT_GENESIS_TX_ID = "e5c8313c4144d219b5f6b2dacf1d36f2d43a9039bb2fcd1bd57f8352a9c9809a";
    private static final int DEFAULT_GENESIS_BLOCK_HEIGHT = 477865; // 2017-07-28


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Getter
    private final String genesisTxId;
    @Getter
    private final int genesisBlockHeight;
    @Getter
    private int chainHeight;
    @Getter
    private final LinkedList<Block> blocks;
    @Getter
    private final LinkedList<Cycle> cycles;
    // We need to maintain the UTXO map here because during parsing the state of UTXo might change and we want to access
    // the actual state change which happens during parsing a block and not the after the final block gets added to the
    // state. If we would access a isUnspent property in TxOutput we would not see that as the block (tx and txOutput)
    // is not added to the state during parsing.
    @Getter
    private final Map<TxOutputKey, TxOutput> unspentTxOutputMap;
    @Getter
    private final Map<TxOutputKey, TxOutput> nonBsqTxOutputMap;
    // TODO SQ i prepared the map for the confist. requests
    @Getter
    private final Map<TxOutputKey, TxOutput> confiscatedTxOutputMap;
    @Getter
    private final Map<String, Issuance> issuanceMap; // key is txId
    @Getter
    private final Map<TxOutputKey, SpentInfo> spentInfoMap;

    @Getter
    private final List<ParamChange> paramChangeList;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public State(@Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                 @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this(genesisTxId,
                genesisBlockHeight,
                genesisBlockHeight,
                new LinkedList<>(),
                new LinkedList<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new ArrayList<>()
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private State(String genesisTxId,
                  int genesisBlockHeight,
                  int chainHeight,
                  LinkedList<Block> blocks,
                  LinkedList<Cycle> cycles,
                  Map<TxOutputKey, TxOutput> unspentTxOutputMap,
                  Map<TxOutputKey, TxOutput> nonBsqTxOutputMap,
                  Map<TxOutputKey, TxOutput> confiscatedTxOutputMap,
                  Map<String, Issuance> issuanceMap,
                  Map<TxOutputKey, SpentInfo> spentInfoMap,
                  List<ParamChange> paramChangeList) {
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
        this.chainHeight = chainHeight;
        this.blocks = blocks;
        this.cycles = cycles;
        this.unspentTxOutputMap = unspentTxOutputMap;
        this.nonBsqTxOutputMap = nonBsqTxOutputMap;
        this.confiscatedTxOutputMap = confiscatedTxOutputMap;
        this.issuanceMap = issuanceMap;
        this.spentInfoMap = spentInfoMap;
        this.paramChangeList = paramChangeList;
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setState(getStateBuilder()).build();
    }

    private PB.State.Builder getStateBuilder() {
        final PB.State.Builder builder = PB.State.newBuilder();
        builder.setGenesisTxId(genesisTxId)
                .setGenesisBlockHeight(genesisBlockHeight)
                .setChainHeight(chainHeight)
                .addAllBlocks(blocks.stream().map(Block::toProtoMessage).collect(Collectors.toList()))
                .addAllCycles(cycles.stream().map(Cycle::toProtoMessage).collect(Collectors.toList()))
                .putAllUnspentTxOutputMap(unspentTxOutputMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toProtoMessage())))
                .putAllUnspentTxOutputMap(nonBsqTxOutputMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toProtoMessage())))
                .putAllUnspentTxOutputMap(confiscatedTxOutputMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toProtoMessage())))
                .putAllIssuanceMap(issuanceMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toProtoMessage())))
                .putAllSpentInfoMap(spentInfoMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), entry -> entry.getValue().toProtoMessage())))
                .addAllParamChangeList(paramChangeList.stream().map(ParamChange::toProtoMessage).collect(Collectors.toList()));
        return builder;
    }

    public static PersistableEnvelope fromProto(PB.State proto) {
        LinkedList<Block> blocks = proto.getBlocksList().stream()
                .map(Block::fromProto)
                .collect(Collectors.toCollection(LinkedList::new));
        final LinkedList<Cycle> cycles = proto.getCyclesList().stream()
                .map(Cycle::fromProto).collect(Collectors.toCollection(LinkedList::new));
        Map<TxOutputKey, TxOutput> unspentTxOutputMap = proto.getUnspentTxOutputMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> TxOutput.fromProto(e.getValue())));
        Map<TxOutputKey, TxOutput> nonBsqTxOutputMap = proto.getNonBsqTxOutputMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> TxOutput.fromProto(e.getValue())));
        Map<TxOutputKey, TxOutput> confiscatedTxOutputMap = proto.getConfiscatedTxOutputMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> TxOutput.fromProto(e.getValue())));
        Map<String, Issuance> issuanceMap = proto.getIssuanceMapMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Issuance.fromProto(e.getValue())));
        Map<TxOutputKey, SpentInfo> spentInfoMap = proto.getSpentInfoMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> SpentInfo.fromProto(e.getValue())));
        final List<ParamChange> paramChangeList = proto.getParamChangeListList().stream()
                .map(ParamChange::fromProto).collect(Collectors.toCollection(ArrayList::new));
        return new State(proto.getGenesisTxId(),
                proto.getGenesisBlockHeight(),
                proto.getChainHeight(),
                blocks,
                cycles,
                unspentTxOutputMap,
                nonBsqTxOutputMap,
                confiscatedTxOutputMap,
                issuanceMap,
                spentInfoMap,
                paramChangeList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope access
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setChainHeight(int chainHeight) {
        this.chainHeight = chainHeight;
    }

    State getClone() {
        return (State) State.fromProto(getStateBuilder().build());
    }

    State getClone(State snapshotCandidate) {
        return (State) State.fromProto(snapshotCandidate.getStateBuilder().build());
    }
}

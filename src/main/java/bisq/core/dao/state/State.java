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
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.period.Cycle;
import bisq.core.dao.voting.proposal.param.ParamChangeMap;

import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;


/**
 * Root class for mutable state of the DAO.
 */
@Slf4j
public class State implements PersistableEnvelope {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final int ISSUANCE_MATURITY = 144 * 30; // 30 days
    public static final Coin GENESIS_TOTAL_SUPPLY = Coin.parseCoin("2.5");

    //mainnet
    // this tx has a lot of outputs
    // https://blockchain.info/de/tx/ee921650ab3f978881b8fe291e0c025e0da2b7dc684003d7a03d9649dfee2e15
    // BLOCK_HEIGHT 411779
    // 411812 has 693 recursions
    // block 376078 has 2843 recursions and caused once a StackOverflowError, a second run worked. Took 1,2 sec.

    // BTC MAIN NET
    public static final String DEFAULT_GENESIS_TX_ID = "e5c8313c4144d219b5f6b2dacf1d36f2d43a9039bb2fcd1bd57f8352a9c9809a";
    public static final int DEFAULT_GENESIS_BLOCK_HEIGHT = 477865; // 2017-07-28


    private final String genesisTxId;
    private final int genesisBlockHeight;
    private int chainHeight;
    private final LinkedList<Block> blocks;
    private final Map<String, TxType> txTypeMap; // key is txId
    private final Map<String, Long> burntFeeMap; // key is txId
    private final Map<String, Integer> issuanceBlockHeightMap; // key is txId of issuance txOutput, value is blockHeight
    private final Map<TxOutput.Key, TxOutput> unspentTxOutputMap;
    private final Map<TxOutput.Key, TxOutputType> txOutputTypeMap;
    private final Map<TxOutput.Key, SpentInfo> spentInfoMap;

    private final LinkedList<Cycle> cycles;

    private final Map<Integer, ParamChangeMap> paramChangeByBlockHeightMap;


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
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new LinkedList<>(),
                new HashMap<>()
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private State(String genesisTxId,
                  int genesisBlockHeight,
                  int chainHeight,
                  LinkedList<Block> blocks,
                  Map<String, TxType> txTypeMap,
                  Map<String, Long> burntFeeMap,
                  Map<String, Integer> issuanceBlockHeightMap,
                  Map<TxOutput.Key, TxOutput> unspentTxOutputMap,
                  Map<TxOutput.Key, TxOutputType> txOutputTypeMap,
                  Map<TxOutput.Key, SpentInfo> spentInfoMap,
                  LinkedList<Cycle> cycles,
                  Map<Integer, ParamChangeMap> paramChangeByBlockHeightMap) {
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
        this.chainHeight = chainHeight;
        this.blocks = blocks;
        this.txTypeMap = txTypeMap;
        this.burntFeeMap = burntFeeMap;
        this.issuanceBlockHeightMap = issuanceBlockHeightMap;
        this.unspentTxOutputMap = unspentTxOutputMap;
        this.txOutputTypeMap = txOutputTypeMap;
        this.spentInfoMap = spentInfoMap;
        this.cycles = cycles;
        this.paramChangeByBlockHeightMap = paramChangeByBlockHeightMap;
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
                .putAllTxTypeMap(txTypeMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toProtoMessage())))
                .putAllBurntFeeMap(burntFeeMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .putAllIssuanceBlockHeightMap(issuanceBlockHeightMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .putAllUnspentTxOutputMap(unspentTxOutputMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), entry -> entry.getValue().toProtoMessage())))
                .putAllTxOutputTypeMap(txOutputTypeMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), entry -> entry.getValue().toProtoMessage())))
                .putAllSpentInfoMap(spentInfoMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), entry -> entry.getValue().toProtoMessage())))
                .addAllCycles(cycles.stream().map(Cycle::toProtoMessage).collect(Collectors.toList()))
                .putAllParamChangeByBlockHeight(paramChangeByBlockHeightMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toProtoMessage())));
        return builder;
    }

    public static PersistableEnvelope fromProto(PB.State proto) {
        LinkedList<Block> blocks = proto.getBlocksList().stream()
                .map(Block::fromProto)
                .collect(Collectors.toCollection(LinkedList::new));
        Map<String, TxType> txTypeMap = proto.getTxTypeMapMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> TxType.fromProto(e.getValue())));
        Map<String, Long> burntFeeMap = proto.getBurntFeeMapMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, Integer> issuanceBlockHeightMap = proto.getIssuanceBlockHeightMapMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<TxOutput.Key, TxOutput> unspentTxOutputMap = proto.getUnspentTxOutputMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutput.Key.getKeyFromString(e.getKey()), e -> TxOutput.fromProto(e.getValue())));
        Map<TxOutput.Key, TxOutputType> txOutputTypeMap = proto.getTxOutputTypeMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutput.Key.getKeyFromString(e.getKey()), e -> TxOutputType.fromProto(e.getValue())));
        Map<TxOutput.Key, SpentInfo> spentInfoMap = proto.getSpentInfoMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutput.Key.getKeyFromString(e.getKey()), e -> SpentInfo.fromProto(e.getValue())));
        final LinkedList<Cycle> cycles = proto.getCyclesList().stream()
                .map(Cycle::fromProto).collect(Collectors.toCollection(LinkedList::new));
        Map<Integer, ParamChangeMap> paramChangeListMap = proto.getParamChangeByBlockHeightMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> ParamChangeMap.fromProto(e.getValue())));
        return new State(proto.getGenesisTxId(),
                proto.getGenesisBlockHeight(),
                proto.getChainHeight(),
                blocks,
                txTypeMap,
                burntFeeMap,
                issuanceBlockHeightMap,
                unspentTxOutputMap,
                txOutputTypeMap,
                spentInfoMap,
                cycles,
                paramChangeListMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Clone
    ///////////////////////////////////////////////////////////////////////////////////////////

    State getClone() {
        return (State) State.fromProto(getStateBuilder().build());
    }

    State getClone(State snapshotCandidate) {
        return (State) State.fromProto(snapshotCandidate.getStateBuilder().build());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setChainHeight(int chainHeight) {
        this.chainHeight = chainHeight;
    }

    void addBlock(Block block) {
        blocks.add(block);
    }

    void setTxType(String txId, TxType txType) {
        txTypeMap.put(txId, txType);
    }

    void setBurntFee(String txId, long burnedFee) {
        burntFeeMap.put(txId, burnedFee);
    }

    void addUnspentTxOutput(TxOutput txOutput) {
        unspentTxOutputMap.put(txOutput.getKey(), txOutput);
    }

    void removeUnspentTxOutput(TxOutput txOutput) {
        unspentTxOutputMap.remove(txOutput.getKey());
    }

    void setIssuanceBlockHeight(TxOutput txOutput, int chainHeight) {
        issuanceBlockHeightMap.put(txOutput.getTxId(), chainHeight);
    }

    void setSpentInfo(TxOutput txOutput, int blockHeight, String txId, int inputIndex) {
        spentInfoMap.put(txOutput.getKey(), new SpentInfo(blockHeight, txId, inputIndex));
    }

    void setTxOutputType(TxOutput txOutput, TxOutputType txOutputType) {
        txOutputTypeMap.put(txOutput.getKey(), txOutputType);
    }

    void addCycle(Cycle cycle) {
        cycles.add(cycle);
    }

    public void setParamChangeMap(int blockHeight, ParamChangeMap paramChangeMap) {
        paramChangeByBlockHeightMap.put(blockHeight, paramChangeMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    int getIssuanceMaturity() {
        return ISSUANCE_MATURITY;
    }

    Coin getGenesisTotalSupply() {
        return GENESIS_TOTAL_SUPPLY;
    }


    String getGenesisTxId() {
        return genesisTxId;
    }

    int getGenesisBlockHeight() {
        return genesisBlockHeight;
    }

    int getChainHeight() {
        return chainHeight;
    }

    LinkedList<Block> getBlocks() {
        return blocks;
    }

    Map<String, TxType> getTxTypeMap() {
        return txTypeMap;
    }

    Map<String, Long> getBurntFeeMap() {
        return burntFeeMap;
    }

    Map<String, Integer> getIssuanceBlockHeightMap() {
        return issuanceBlockHeightMap;
    }

    Map<TxOutput.Key, TxOutput> getUnspentTxOutputMap() {
        return unspentTxOutputMap;
    }

    Map<TxOutput.Key, TxOutputType> getTxOutputTypeMap() {
        return txOutputTypeMap;
    }

    Map<TxOutput.Key, SpentInfo> getSpentInfoMap() {
        return spentInfoMap;
    }

    LinkedList<Cycle> getCycles() {
        return cycles;
    }

    Map<Integer, ParamChangeMap> getParamChangeByBlockHeightMap() {
        return paramChangeByBlockHeightMap;
    }
}

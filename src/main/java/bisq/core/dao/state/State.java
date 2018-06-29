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
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.ext.Issuance;
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
    @Getter
    private final Map<String, MutableTx> mutableTxMap; // key is txId
    @Getter
    private final Map<String, Issuance> issuanceMap; // key is txId
    @Getter
    private final Map<TxOutput.Key, SpentInfo> spentInfoMap;

    // TODO might get refactored later when working on params
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
                new LinkedList<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
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
                  LinkedList<Cycle> cycles,
                  Map<String, MutableTx> mutableTxMap,
                  Map<String, Issuance> issuanceMap,
                  Map<TxOutput.Key, SpentInfo> spentInfoMap,
                  Map<Integer, ParamChangeMap> paramChangeByBlockHeightMap) {
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
        this.chainHeight = chainHeight;
        this.blocks = blocks;
        this.cycles = cycles;
        this.mutableTxMap = mutableTxMap;
        this.issuanceMap = issuanceMap;
        this.spentInfoMap = spentInfoMap;
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
                .addAllCycles(cycles.stream().map(Cycle::toProtoMessage).collect(Collectors.toList()))
                .putAllMutableTxMap(mutableTxMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> {
                            Tx tx = e.getValue().getTx();
                            return tx.getId();
                        }, e -> e.getValue().toProtoMessage())))
                .putAllIssuanceMap(issuanceMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toProtoMessage())))
                .putAllSpentInfoMap(spentInfoMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), entry -> entry.getValue().toProtoMessage())))
                .putAllParamChangeByBlockHeight(paramChangeByBlockHeightMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toProtoMessage())));
        return builder;
    }

    public static PersistableEnvelope fromProto(PB.State proto) {
        LinkedList<Block> blocks = proto.getBlocksList().stream()
                .map(Block::fromProto)
                .collect(Collectors.toCollection(LinkedList::new));
        final LinkedList<Cycle> cycles = proto.getCyclesList().stream()
                .map(Cycle::fromProto).collect(Collectors.toCollection(LinkedList::new));
        Map<String, MutableTx> mutableTxMap = proto.getMutableTxMapMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> MutableTx.fromProto(e.getValue())));
        Map<String, Issuance> issuanceMap = proto.getIssuanceMapMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Issuance.fromProto(e.getValue())));
        Map<TxOutput.Key, SpentInfo> spentInfoMap = proto.getSpentInfoMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutput.Key.getKeyFromString(e.getKey()), e -> SpentInfo.fromProto(e.getValue())));
        Map<Integer, ParamChangeMap> paramChangeListMap = proto.getParamChangeByBlockHeightMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> ParamChangeMap.fromProto(e.getValue())));
        return new State(proto.getGenesisTxId(),
                proto.getGenesisBlockHeight(),
                proto.getChainHeight(),
                blocks,
                cycles,
                mutableTxMap,
                issuanceMap,
                spentInfoMap,
                paramChangeListMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope access
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setChainHeight(int chainHeight) {
        this.chainHeight = chainHeight;
    }

    void addBlock(Block block) {
        blocks.add(block);
    }

    public void putMutableTx(String txId, MutableTx mutableTx) {
        mutableTxMap.put(txId, mutableTx);
    }

    void putSpentInfo(TxOutput.Key txOutputKey, SpentInfo spentInfo) {
        spentInfoMap.put(txOutputKey, spentInfo);
    }

    void addIssuance(Issuance issuance) {
        issuanceMap.put(issuance.getTxId(), issuance);
    }

    void addCycle(Cycle cycle) {
        cycles.add(cycle);
    }


    State getClone() {
        return (State) State.fromProto(getStateBuilder().build());
    }

    State getClone(State snapshotCandidate) {
        return (State) State.fromProto(snapshotCandidate.getStateBuilder().build());
    }


    // TODO might get refactored later when working on params
    public void setParamChangeMap(int blockHeight, ParamChangeMap paramChangeMap) {
        paramChangeByBlockHeightMap.put(blockHeight, paramChangeMap);
    }

    Map<Integer, ParamChangeMap> getParamChangeByBlockHeightMap() {
        return paramChangeByBlockHeightMap;
    }
}

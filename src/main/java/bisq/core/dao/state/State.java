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
import bisq.core.dao.period.Cycle;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.voting.proposal.Proposal;

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
    private final LinkedList<Block> blocks;
    private final Map<String, TxType> txTypeMap;
    private final Map<String, Long> burntFeeMap;
    private final Map<String, Integer> issuanceBlockHeightMap;
    private final Map<TxOutput.Key, TxOutput> unspentTxOutputMap;
    private final Map<TxOutput.Key, TxOutputType> txOutputTypeMap;
    private final Map<TxOutput.Key, SpentInfo> spentInfoMap;

    //TODO not in PB yet as not clear if we keep it
    private final Map<String, Proposal> proposalPayloadMap;
    private final List<Cycle> cycles;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public State(@Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                 @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this(genesisTxId,
                genesisBlockHeight,
                new LinkedList<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new ArrayList<>());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private State(String genesisTxId,
                  int genesisBlockHeight,
                  LinkedList<Block> blocks,
                  Map<String, TxType> txTypeMap,
                  Map<String, Long> burntFeeMap,
                  Map<String, Integer> issuanceBlockHeightMap,
                  Map<TxOutput.Key, TxOutput> unspentTxOutputMap,
                  Map<TxOutput.Key, TxOutputType> txOutputTypeMap,
                  Map<TxOutput.Key, SpentInfo> spentInfoMap,
                  Map<String, Proposal> proposalPayloadMap,
                  List<Cycle> cycles) {
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
        this.blocks = blocks;
        this.txTypeMap = txTypeMap;
        this.burntFeeMap = burntFeeMap;
        this.issuanceBlockHeightMap = issuanceBlockHeightMap;
        this.unspentTxOutputMap = unspentTxOutputMap;
        this.txOutputTypeMap = txOutputTypeMap;
        this.spentInfoMap = spentInfoMap;
        this.proposalPayloadMap = proposalPayloadMap;
        this.cycles = cycles;
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setState(getStateBuilder()).build();
    }

    private PB.State.Builder getStateBuilder() {
        final PB.State.Builder builder = PB.State.newBuilder();
        builder.setGenesisTxId(genesisTxId)
                .setGenesisBlockHeight(genesisBlockHeight)
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
                        .collect(Collectors.toMap(e -> e.getKey().toString(), entry -> entry.getValue().toProtoMessage())));
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
        return new State(proto.getGenesisTxId(),
                proto.getGenesisBlockHeight(),
                blocks,
                txTypeMap,
                burntFeeMap,
                issuanceBlockHeightMap,
                unspentTxOutputMap,
                txOutputTypeMap,
                spentInfoMap,
                null,
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO remove
    int getChainHeadHeight() {
        return !getBlocks().isEmpty() ? getBlocks().getLast().getHeight() : 0;
    }

    State getClone() {
        //TODO
        return this;
        // return lock.read(() -> (StateService) StateService.fromProto(stateService.getStateBuilder().build());
    }

    //TODO
    State getClone(State snapshotCandidate) {
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope write access
    ///////////////////////////////////////////////////////////////////////////////////////////

    void addBlock(Block block) {
        blocks.add(block);
    }

    void putTxType(String txId, TxType txType) {
        txTypeMap.put(txId, txType);
    }

    void putBurntFee(String txId, long burnedFee) {
        burntFeeMap.put(txId, burnedFee);
    }

    void addUnspentTxOutput(TxOutput txOutput) {
        unspentTxOutputMap.put(txOutput.getKey(), txOutput);
    }

    void removeUnspentTxOutput(TxOutput txOutput) {
        unspentTxOutputMap.remove(txOutput.getKey());
    }

    void putIssuanceBlockHeight(TxOutput txOutput, int chainHeight) {
        issuanceBlockHeightMap.put(txOutput.getTxId(), chainHeight);
    }

    void putSpentInfo(TxOutput txOutput, int blockHeight, String txId, int inputIndex) {
        spentInfoMap.put(txOutput.getKey(), new SpentInfo(blockHeight, txId, inputIndex));
    }

    void putTxOutputType(TxOutput txOutput, TxOutputType txOutputType) {
        txOutputTypeMap.put(txOutput.getKey(), txOutputType);
    }

    void addCycle(Cycle cycle) {
        cycles.add(cycle);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    String getGenesisTxId() {
        return genesisTxId;
    }

    int getGenesisBlockHeight() {
        return genesisBlockHeight;
    }

    int getIssuanceMaturity() {
        return ISSUANCE_MATURITY;
    }

    Coin getGenesisTotalSupply() {
        return GENESIS_TOTAL_SUPPLY;
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

    Map<String, Proposal> getProposalPayloadMap() {
        return proposalPayloadMap;
    }

    List<Cycle> getCycles() {
        return cycles;
    }
}

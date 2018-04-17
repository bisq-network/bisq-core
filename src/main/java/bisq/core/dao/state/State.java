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
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.vote.period.Cycle;
import bisq.core.dao.vote.proposal.ProposalPayload;

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
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;


/**
 * Root class for mutable state of the DAO.
 *
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

    // We need to have a threadsafe list here as we might get added a listener from user thread during iteration
    // at parser thread.
    private final List<StateChangeListener> stateChangeListeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Called from user thread.
    public void addStateChangeListener(StateChangeListener stateChangeListener) {
        stateChangeListeners.add(stateChangeListener);
    }


    private final String genesisTxId;
    private final int genesisBlockHeight;

    // Immutable data
    // The block is the fundamental data structure for state transition.
    // It consists of the txBlock for blockchain related data and the stateChangeEvents for non-blockchain related
    // data like Proposals, BlindVotes or ParamChangeEvents.
    private final LinkedList<Block> blocks = new LinkedList<>();

    // Mutable data

    // Blockchain related data

    // Tx specific, key is txId
    private final Map<String, TxType> txTypeMap = new HashMap<>();
    private final Map<String, Long> burntFeeMap = new HashMap<>();
    private final Map<String, Integer> issuanceBlockHeightMap = new HashMap<>();

    // TxOutput specific
    private final Map<TxOutput.Key, TxOutput> unspentTxOutputMap = new HashMap<>();
    private final Map<TxOutput.Key, TxOutputType> txOutputTypeMap = new HashMap<>();
    private final Map<TxOutput.Key, SpentInfo> spentInfoMap = new HashMap<>();

    // StateChangeEvents (non blockchain data)
    private final List<Cycle> cycles = new ArrayList<>();
    private final Map<String, ProposalPayload> proposalPayloadMap = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public State(@Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                 @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

 /*   private State(LinkedList<Block> blocks,
                  Map<TxOutput.Key, TxOutput> unspentTxOutputMap) {
        this.blocks = blocks;
        this.unspentTxOutputMap = unspentTxOutputMap;

    }*/

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
                        .collect(Collectors.toMap(Map.Phase::getKey,
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
                        .collect(Collectors.toMap(Map.Phase::getKey, v -> Tx.fromProto(v.getValue())))),
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

    //TODO remove
    public int getChainHeadHeight() {
        return !getBlocks().isEmpty() ? getBlocks().getLast().getHeight() : 0;
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Changing map state package scope
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addBlock(Block block) {
        blocks.add(block);
        stateChangeListeners.forEach(listener -> listener.execute(() -> listener.onAddBlock(block)));
    }

    void putTxType(String txId, TxType txType) {
        txTypeMap.put(txId, txType);
        stateChangeListeners.forEach(listener -> listener.execute(() -> listener.onPutTxType(txId, txType)));
    }

    void putBurntFee(String txId, long burnedFee) {
        burntFeeMap.put(txId, burnedFee);
        stateChangeListeners.forEach(listener -> listener.execute(() -> listener.onPutBurntFee(txId, burnedFee)));
    }

    void addUnspentTxOutput(TxOutput txOutput) {
        unspentTxOutputMap.put(txOutput.getKey(), txOutput);
        stateChangeListeners.forEach(listener -> listener.execute(() -> listener.onAddUnspentTxOutput(txOutput)));
    }

    void removeUnspentTxOutput(TxOutput txOutput) {
        unspentTxOutputMap.remove(txOutput.getKey());
        stateChangeListeners.forEach(listener -> listener.execute(() -> listener.onRemoveUnspentTxOutput(txOutput)));
    }

    void putIssuanceBlockHeight(TxOutput txOutput, int chainHeight) {
        issuanceBlockHeightMap.put(txOutput.getTxId(), chainHeight);
        stateChangeListeners.forEach(listener -> listener.execute(() -> listener.onPutIssuanceBlockHeight(txOutput, chainHeight)));
    }

    void putSpentInfo(TxOutput txOutput, int blockHeight, String txId, int inputIndex) {
        spentInfoMap.put(txOutput.getKey(), new SpentInfo(blockHeight, txId, inputIndex));
        stateChangeListeners.forEach(listener -> listener.execute(() -> listener.onPutSpentInfo(txOutput, blockHeight, txId, inputIndex)));
    }

    void putTxOutputType(TxOutput txOutput, TxOutputType txOutputType) {
        txOutputTypeMap.put(txOutput.getKey(), txOutputType);
        stateChangeListeners.forEach(listener -> listener.execute(() -> listener.onPutTxOutputType(txOutput, txOutputType)));
    }

    void addCycle(Cycle cycle) {
        cycles.add(cycle);
        stateChangeListeners.forEach(listener -> listener.execute(() -> listener.onAddCycle(cycle)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters package scope
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

    Map<String, ProposalPayload> getProposalPayloadMap() {
        return proposalPayloadMap;
    }

    List<Cycle> getCycles() {
        return cycles;
    }
}


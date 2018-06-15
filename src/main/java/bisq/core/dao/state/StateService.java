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

import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.ext.Issuance;
import bisq.core.dao.state.period.Cycle;
import bisq.core.dao.state.period.CycleService;
import bisq.core.dao.voting.proposal.param.Param;
import bisq.core.dao.voting.proposal.param.ParamChangeMap;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StateService {
    private final State state;
    private final CycleService cycleService;

    // TODO used only by snapshot manager
    private final List<BlockListener> blockListeners = new CopyOnWriteArrayList<>();
    private final List<ChainHeightListener> chainHeightListeners = new CopyOnWriteArrayList<>();
    private final List<ParseBlockChainListener> parseBlockChainListeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public StateService(State state, CycleService cycleService) {
        super();

        this.state = state;
        this.cycleService = cycleService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addBlockListener(BlockListener listener) {
        blockListeners.add(listener);
    }

    public void removeBlockListener(BlockListener listener) {
        blockListeners.remove(listener);
    }

    public void addChainHeightListener(ChainHeightListener listener) {
        chainHeightListeners.add(listener);
    }

    public void removeChainHeightListener(ChainHeightListener listener) {
        chainHeightListeners.remove(listener);
    }

    public void addParseBlockChainListener(ParseBlockChainListener listener) {
        parseBlockChainListeners.add(listener);
    }

    public void removeParseBlockChainListener(ParseBlockChainListener listener) {
        parseBlockChainListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Snapshot
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot(State snapshot) {
        state.setChainHeight(snapshot.getChainHeight());
        state.getBlocks().clear();
        state.getBlocks().addAll(snapshot.getBlocks());
        state.getTxTypeMap().clear();
        state.getTxTypeMap().putAll(snapshot.getTxTypeMap());
        state.getBurntFeeMap().clear();
        state.getBurntFeeMap().putAll(snapshot.getBurntFeeMap());
        state.getIssuanceMap().clear();
        state.getIssuanceMap().putAll(snapshot.getIssuanceMap());
        state.getUnspentTxOutputMap().clear();
        state.getUnspentTxOutputMap().putAll(snapshot.getUnspentTxOutputMap());
        state.getTxOutputTypeMap().clear();
        state.getTxOutputTypeMap().putAll(snapshot.getTxOutputTypeMap());
        state.getSpentInfoMap().clear();
        state.getSpentInfoMap().putAll(snapshot.getSpentInfoMap());
        state.getCycles().clear();
        state.getCycles().addAll(snapshot.getCycles());
        state.getParamChangeByBlockHeightMap().clear();
        state.getParamChangeByBlockHeightMap().putAll(snapshot.getParamChangeByBlockHeightMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialize
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        final int genesisBlockHeight = state.getGenesisBlockHeight();
        state.addCycle(cycleService.getFirstCycle(genesisBlockHeight));
        state.setChainHeight(genesisBlockHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Parser sets blockHeight of new block to be parsed
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setNewBlockHeight(int blockHeight) {
        if (blockHeight != state.getGenesisBlockHeight())
            cycleService.maybeCreateNewCycle(blockHeight, getCycles(), state.getParamChangeByBlockHeightMap())
                    .ifPresent(state::addCycle);

        state.setChainHeight(blockHeight);
        chainHeightListeners.forEach(listener -> listener.onChainHeightChanged(blockHeight));
    }

    public void addNewBlock(Block block) {
        state.addBlock(block);
        blockListeners.forEach(l -> l.onBlockAdded(block));
        log.info("New Block added at blockHeight " + block.getHeight());
    }

    public void onParseBlockChainComplete() {
        parseBlockChainListeners.forEach(ParseBlockChainListener::onComplete);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Modify state
    ///////////////////////////////////////////////////////////////////////////////////////////


    public List<Block> getClonedBlocksFrom(int fromBlockHeight) {
        final LinkedList<Block> clonedBlocks = new LinkedList<>(getBlocks());
        return clonedBlocks.stream()
                .filter(block -> block.getHeight() >= fromBlockHeight)
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTxType(String txId, TxType txType) {
        state.setTxType(txId, txType);
    }


    public void setBurntFee(String txId, long burnedFee) {
        state.setBurntFee(txId, burnedFee);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addUnspentTxOutput(TxOutput txOutput) {
        state.addUnspentTxOutput(txOutput);
    }

    public void removeUnspentTxOutput(TxOutput txOutput) {
        state.removeUnspentTxOutput(txOutput);
    }

    public void addIssuance(Issuance issuance) {
        TxOutput txOutput = issuance.getTxOutput();
        state.addIssuance(issuance);
        state.addUnspentTxOutput(txOutput);
    }

    public void setSpentInfo(TxOutput txOutput, int blockHeight, String txId, int inputIndex) {
        state.setSpentInfo(txOutput, blockHeight, txId, inputIndex);
    }

    public void setTxOutputType(TxOutput txOutput, TxOutputType txOutputType) {
        state.setTxOutputType(txOutput, txOutputType);
    }

    public void setParamChangeMap(int chainHeight, ParamChangeMap paramChangeMap) {
        state.setParamChangeMap(chainHeight, paramChangeMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // State
    public State getClone() {
        return state.getClone();
    }

    // Genesis
    public String getGenesisTxId() {
        return state.getGenesisTxId();
    }

    public int getGenesisBlockHeight() {
        return state.getGenesisBlockHeight();
    }

    public Coin getGenesisTotalSupply() {
        return state.getGenesisTotalSupply();
    }

    // Block
    public LinkedList<Block> getBlocks() {
        return state.getBlocks();
    }

    public LinkedList<Block> getBlockFromState(State state) {
        return new LinkedList<>(state.getBlocks());
    }

    // Tx
    public Map<String, TxType> getTxTypeMap() {
        return state.getTxTypeMap();
    }

    public Map<String, Long> getBurntFeeMap() {
        return state.getBurntFeeMap();
    }

    public Map<TxOutput.Key, Issuance> getIssuanceMap() {
        return state.getIssuanceMap();
    }

    // TxOutput
    public Map<TxOutput.Key, TxOutput> getUnspentTxOutputMap() {
        return state.getUnspentTxOutputMap();
    }

    public Map<TxOutput.Key, SpentInfo> getTxOutputSpentInfoMap() {
        return state.getSpentInfoMap();
    }

    public Map<TxOutput.Key, TxOutputType> getTxOutputTypeMap() {
        return state.getTxOutputTypeMap();
    }

    // Cycle
    public LinkedList<Cycle> getCycles() {
        return state.getCycles();
    }

    public Cycle getCurrentCycle() {
        return getCycles().getLast();
    }

    public int getChainHeight() {
        return state.getChainHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Block
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Block> getBlockAtHeight(int height) {
        return getBlocks().stream()
                .filter(block -> block.getHeight() == height)
                .findAny();
    }

    public boolean containsBlock(Block block) {
        return getBlocks().contains(block);
    }

    public boolean containsBlockHash(String blockHash) {
        return getBlocks().stream().anyMatch(block -> block.getHash().equals(blockHash));
    }

    public long getBlockTime(int height) {
        return getBlockAtHeight(height).map(Block::getTime).orElse(0L);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Tx> getGenesisTx() {
        return getTx(getGenesisTxId());
    }

    public boolean isIssuanceTx(String txId) {
        return getIssuance(txId).isPresent();
    }

    public int getIssuanceBlockHeight(String txId) {
        return getIssuance(txId)
                .map(Issuance::getChainHeight)
                .orElse(0);
    }

    private Optional<Issuance> getIssuance(String txId) {
        return getIssuanceMap().entrySet().stream()
                .filter(entry -> entry.getKey().getTxId().equals(txId))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public Optional<Tx> getTx(String txId) {
        return Optional.ofNullable(getTxMap().get(txId));
    }

    public Map<String, Tx> getTxMap() {
        return getBlocks().stream()
                .flatMap(block -> block.getTxs().stream())
                .collect(Collectors.toMap(Tx::getId, tx -> tx));
    }

    public Set<Tx> getTxs() {
        return getBlocks().stream()
                .flatMap(block -> block.getTxs().stream())
                .collect(Collectors.toSet());
    }

    public Set<Tx> getFeeTxs() {
        return getBlocks().stream()
                .flatMap(block -> block.getTxs().stream())
                .filter(tx -> hasTxBurntFee(tx.getId()))
                .collect(Collectors.toSet());
    }

    public boolean hasTxBurntFee(String txId) {
        return getBurntFee(txId) > 0;
    }

    public long getBurntFee(String txId) {
        return getBurntFeeMap().getOrDefault(txId, 0L);
    }

    public long getTotalBurntFee() {
        return getBurntFeeMap().values().stream()
                .mapToLong(fee -> fee)
                .sum();
    }

    public boolean containsTx(String txId) {
        return getTx(txId).isPresent();
    }

    public Optional<TxType> getTxType(String txId) {
        return Optional.ofNullable(getTxTypeMap().getOrDefault(txId, null));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxInput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxOutput> getConnectedTxOutput(TxInput txInput) {
        return getTx(txInput.getConnectedTxOutputTxId())
                .map(tx -> tx.getOutputs().get(txInput.getConnectedTxOutputIndex()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Block getLastBlock() {
        return this.getBlocks().getLast();
    }

    public Set<TxOutput> getTxOutputs() {
        return getTxMap().values().stream()
                .flatMap(tx -> tx.getOutputs().stream())
                .collect(Collectors.toSet());
    }

    public boolean isUnspent(TxOutput txOutput) {
        return getUnspentTxOutputMap().containsKey(txOutput.getKey());
    }

    public TxOutputType getTxOutputType(TxOutput txOutput) {
        return getTxOutputTypeMap().get(txOutput.getKey());
    }

    public boolean isBsqTxOutputType(TxOutput txOutput) {
        final TxOutputType txOutputType = getTxOutputType(txOutput);

        if (txOutputType == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT)
            return isIssuanceTx(txOutput.getTxId());

        return (txOutputType != TxOutputType.UNDEFINED &&
                txOutputType != TxOutputType.BTC_OUTPUT &&
                txOutputType != TxOutputType.INVALID_OUTPUT);
    }

    public boolean isTxOutputSpendable(String txId, int index) {
        return getUnspentAndMatureTxOutput(txId, index)
                .filter(txOutput -> getTxOutputType(txOutput) != TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT)
                .isPresent();
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return new HashSet<>(getUnspentTxOutputMap().values());
    }

    public Set<TxOutput> getUnspentBlindVoteStakeTxOutputs() {
        return getUnspentTxOutputMap().values().stream()
                .filter(txOutput -> getTxOutputType(txOutput) == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT)
                .collect(Collectors.toSet());
    }

    public Set<TxOutput> getLockedInBondOutputs() {
        return getUnspentTxOutputMap().values().stream()
                .filter(txOutput -> getTxOutputType(txOutput) == TxOutputType.BOND_LOCK)
                .collect(Collectors.toSet());
    }

    public Optional<TxOutput> getUnspentAndMatureTxOutput(TxOutput.Key key) {
        return getUnspentTxOutput(key).filter(this::isTxOutputMature);
    }

    public Optional<TxOutput> getUnspentTxOutput(TxOutput.Key key) {
        return Optional.ofNullable(getUnspentTxOutputMap().getOrDefault(key, null));
    }

    public Optional<TxOutput> getUnspentAndMatureTxOutput(String txId, int index) {
        return getUnspentAndMatureTxOutput(new TxOutput.Key(txId, index));
    }

    public Set<TxOutput> getVoteRevealOpReturnTxOutputs() {
        return getTxOutputs().stream()
                .filter(txOutput -> getTxOutputType(txOutput) == TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT)
                .collect(Collectors.toSet());
    }

    // We don't use getVerifiedTxOutputs as out output is not a valid BSQ output before the issuance.
    // We marked it only as candidate for issuance and after voting result is applied it might change it's state.
    public Set<TxOutput> getIssuanceCandidateTxOutputs() {
        return getTxOutputs().stream()
                .filter(e -> getTxOutputType(e) == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT)
                .collect(Collectors.toSet());
    }

    public long getTotalIssuedAmountFromCompRequests() {
        return getIssuanceCandidateTxOutputs().stream()
                /*.filter(txOutput -> getTx(txOutput.getTxId()).isPresent())*/ // probably not needed but cross check in parser
                .filter(txOutput -> isIssuanceTx(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum();
    }

    //TODO
    // for genesis we don't need it and for issuance we need more implemented first
    public boolean isTxOutputMature(TxOutput txOutput) {
        return true;
    }

    public Optional<SpentInfo> getSpentInfo(TxOutput txOutput) {
        return Optional.ofNullable(getTxOutputSpentInfoMap().getOrDefault(txOutput.getKey(), null));
    }


    public Map<Integer, ParamChangeMap> getParamChangeByBlockHeightMap() {
        return state.getParamChangeByBlockHeightMap();
    }

    public long getParamValue(Param param, int blockHeight) {
        if (state.getParamChangeByBlockHeightMap().containsKey(blockHeight)) {
            final ParamChangeMap paramChangeMap = state.getParamChangeByBlockHeightMap().get(blockHeight);
            return paramChangeMap.getMap().get(param);
        } else {
            return param.getDefaultValue();
        }
    }
}


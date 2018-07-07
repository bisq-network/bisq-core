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
import bisq.core.dao.state.blockchain.MutableTx;
import bisq.core.dao.state.blockchain.MutableTxOutput;
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
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StateService {
    private final State state;
    private final CycleService cycleService;

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

    public void onParseBlockChainComplete() {
        parseBlockChainListeners.forEach(ParseBlockChainListener::onComplete);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Snapshot
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot(State snapshot) {
        state.setChainHeight(snapshot.getChainHeight());

        state.getBlocks().clear();
        state.getBlocks().addAll(snapshot.getBlocks());

        state.getCycles().clear();
        state.getCycles().addAll(snapshot.getCycles());

        state.getMutableTxMap().clear();
        state.getMutableTxMap().putAll(snapshot.getMutableTxMap());

        state.getIssuanceMap().clear();
        state.getIssuanceMap().putAll(snapshot.getIssuanceMap());

        state.getSpentInfoMap().clear();
        state.getSpentInfoMap().putAll(snapshot.getSpentInfoMap());

        state.getParamChangeByBlockHeightMap().clear();
        state.getParamChangeByBlockHeightMap().putAll(snapshot.getParamChangeByBlockHeightMap());
    }

    public State getClone() {
        return state.getClone();
    }

    public LinkedList<Block> getBlocksFromState(State state) {
        return new LinkedList<>(state.getBlocks());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ChainHeight
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int getChainHeight() {
        return state.getChainHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public LinkedList<Cycle> getCycles() {
        return state.getCycles();
    }

    public Cycle getCurrentCycle() {
        return getCycles().getLast();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Block
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addNewBlock(Block block) {
        state.addBlock(block);
        blockListeners.forEach(l -> l.onBlockAdded(block));
        log.info("New Block added at blockHeight " + block.getHeight());
    }

    public LinkedList<Block> getBlocks() {
        return state.getBlocks();
    }

    public Block getLastBlock() {
        return getBlocks().getLast();
    }

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

    public List<Block> getBlocksFromBlockHeight(int fromBlockHeight) {
        return getBlocks().stream()
                .filter(block -> block.getHeight() >= fromBlockHeight)
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Genesis
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getGenesisTxId() {
        return state.getGenesisTxId();
    }

    public int getGenesisBlockHeight() {
        return state.getGenesisBlockHeight();
    }

    public Coin getGenesisTotalSupply() {
        return state.getGenesisTotalSupply();
    }

    public Optional<Tx> getGenesisTx() {
        return getTx(getGenesisTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Tx> getTx(String txId) {
        return Optional.ofNullable(getTxMap().get(txId));
    }

    public Set<Tx> getTxs() {
        return getTxStream().collect(Collectors.toSet());
    }

    public boolean containsTx(String txId) {
        return getTx(txId).isPresent();
    }

    private Stream<Tx> getTxStream() {
        return getBlocks().stream()
                .flatMap(block -> block.getTxs().stream());
    }

    private Map<String, Tx> getTxMap() {
        return getTxStream().collect(Collectors.toMap(Tx::getId, tx -> tx));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxInput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxOutput> getConnectedTxOutput(TxInput txInput) {
        return getTx(txInput.getConnectedTxOutputTxId())
                .map(tx -> tx.getOutputs().get(txInput.getConnectedTxOutputIndex()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MutableTx
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We must add the tx before accessing any other fields inside a MutableTx as the immutable tx will be added to the
    // block only after parsing of the tx is complete and if it was a BSQ tx.
    public void addMutableTx(MutableTx mutableTx) {
        state.putMutableTx(mutableTx.getTx().getId(), mutableTx);
    }

    private Optional<MutableTx> getOptionalMutableTx(String txId) {
        return Optional.ofNullable(state.getMutableTxMap().get(txId));
    }

    private Stream<MutableTx> getMutableTxMapStream() {
        return state.getMutableTxMap().values().stream();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxType (from MutableTx)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxType> getOptionalTxType(String txId) {
        return getOptionalMutableTx(txId).map(MutableTx::getTxType);
    }

    public TxType getTxType(String txId) {
        return getOptionalMutableTx(txId).map(MutableTx::getTxType).orElse(TxType.UNDEFINED_TX_TYPE);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BurntFee (from MutableTx)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getBurntFee(String txId) {
        return getOptionalMutableTx(txId).map(MutableTx::getBurntFee).orElse(0L);
    }

    public boolean hasTxBurntFee(String txId) {
        return getBurntFee(txId) > 0;
    }

    public long getTotalBurntFee() {
        return getMutableTxMapStream()
                .mapToLong(MutableTx::getBurntFee)
                .sum();
    }

    public Set<Tx> getBurntFeeTxs() {
        return getMutableTxMapStream()
                .filter(mutableTx -> mutableTx.getBurntFee() > 0)
                .map(MutableTx::getTx)
                .collect(Collectors.toSet());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MutableTxOutput (from MutableTx)
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Optional<MutableTxOutput> getOptionalMutableTxOutput(TxOutput txOutput) {
        Optional<Map<Integer, MutableTxOutput>> optional = getOptionalMutableTx(txOutput.getTxId()).map(MutableTx::getMutableTxOutputMap);
        if (optional.isPresent()) {
            Map<Integer, MutableTxOutput> mutableTxOutputMap = optional.get();
            mutableTxOutputMap.putIfAbsent(txOutput.getIndex(), new MutableTxOutput(txOutput));
            return Optional.of(mutableTxOutputMap.get(txOutput.getIndex()));
        } else {
            return Optional.empty();
        }
    }

    private Stream<MutableTxOutput> getMutableTxOutputStream() {
        return getMutableTxMapStream()
                .flatMap(mutableTx -> mutableTx.getMutableTxOutputMap().values().stream());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UnspentTxOutput (from MutableTxOutput)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addUnspentTxOutput(TxOutput txOutput) {
        getOptionalMutableTxOutput(txOutput).ifPresent(mutableTxOutput -> mutableTxOutput.setUnspent(true));
    }

    public void removeUnspentTxOutput(TxOutput txOutput) {
        getOptionalMutableTxOutput(txOutput).ifPresent(mutableTxOutput -> mutableTxOutput.setUnspent(false));
    }

    public boolean isUnspent(TxOutput txOutput) {
        return getOptionalMutableTxOutput(txOutput).map(MutableTxOutput::isUnspent).orElse(false);
    }

    public boolean isTxOutputSpendable(String txId, int index) {
        TxOutput.Key key = new TxOutput.Key(txId, index);
        if (!getUnspentMutableTxOutputMap().containsKey(key))
            return false;

        MutableTxOutput mutableTxOutput = getUnspentMutableTxOutputMap().get(key);
        TxOutputType txOutputType = mutableTxOutput.getTxOutputType();
        if (txOutputType == null)
            return false;

        switch (txOutputType) {
            case UNDEFINED:
                return false;
            case GENESIS_OUTPUT:
            case BSQ_OUTPUT:
            case BTC_OUTPUT:
            case PROPOSAL_OP_RETURN_OUTPUT:
            case COMP_REQ_OP_RETURN_OUTPUT:
            case ISSUANCE_CANDIDATE_OUTPUT:
                return true;
            case BLIND_VOTE_LOCK_STAKE_OUTPUT:
                return false;
            case BLIND_VOTE_OP_RETURN_OUTPUT:
            case VOTE_REVEAL_UNLOCK_STAKE_OUTPUT:
            case VOTE_REVEAL_OP_RETURN_OUTPUT:
                return true;
            case LOCKUP:
                return false;
            case LOCKUP_OP_RETURN_OUTPUT:
                return true;
            case UNLOCK:
                Optional<Integer> opUnlockBlockHeight = getUnlockBlockHeight(mutableTxOutput.getTxOutput().getTxId());
                //TODO SQ: is getChainHeight() > opUnlockBlockHeight.get() correct?
                return opUnlockBlockHeight.isPresent() && getChainHeight() > opUnlockBlockHeight.get();
            case INVALID_OUTPUT:
                return false;
            default:
                return false;
        }
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return new HashSet<>(getUnspentTxOutputMap().values());
    }

    public Optional<TxOutput> getUnspentTxOutput(TxOutput.Key key) {
        return Optional.ofNullable(getUnspentTxOutputMap().getOrDefault(key, null));
    }

    private Map<TxOutput.Key, TxOutput> getUnspentTxOutputMap() {
        return getMutableTxOutputStream()
                .filter(MutableTxOutput::isUnspent)
                .map(MutableTxOutput::getTxOutput)
                .collect(Collectors.toMap(TxOutput::getKey, v -> v));
    }

    private Map<TxOutput.Key, MutableTxOutput> getUnspentMutableTxOutputMap() {
        return getMutableTxOutputStream()
                .filter(MutableTxOutput::isUnspent)
                .collect(Collectors.toMap(mutableTxOutput -> mutableTxOutput.getTxOutput().getKey(),
                        mutableTxOutput -> mutableTxOutput));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutputType(from MutableTxOutput)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTxOutputType(TxOutput txOutput, TxOutputType txOutputType) {
        getOptionalMutableTxOutput(txOutput).ifPresent(mutableTxOutput -> mutableTxOutput.setTxOutputType(txOutputType));
    }

    /**
     * @param txOutput The txOutput we want to look up.
     * @return the TxOutputType of MutableTxOutput entry. Return TxOutputType.UNDEFINED if not set.
     */
    public TxOutputType getTxOutputType(TxOutput txOutput) {
        return getOptionalMutableTxOutput(txOutput).map(MutableTxOutput::getTxOutputType).orElse(TxOutputType.UNDEFINED);
    }

    private Set<TxOutput> getTxOutputsByTxOutputType(TxOutputType txOutputType) {
        return getMutableTxOutputStream()
                .filter(mutableTxOutput -> mutableTxOutput.getTxOutputType() == txOutputType)
                .map(MutableTxOutput::getTxOutput)
                .collect(Collectors.toSet());
    }

    public boolean isBsqTxOutputType(TxOutput txOutput) {
        final TxOutputType txOutputType = getTxOutputType(txOutput);
        switch (txOutputType) {
            case UNDEFINED:
                return false;
            case GENESIS_OUTPUT:
            case BSQ_OUTPUT:
                return true;
            case BTC_OUTPUT:
                return false;
            case PROPOSAL_OP_RETURN_OUTPUT:
            case COMP_REQ_OP_RETURN_OUTPUT:
                return true;
            case ISSUANCE_CANDIDATE_OUTPUT:
                return isIssuanceTx(txOutput.getTxId());
            case BLIND_VOTE_LOCK_STAKE_OUTPUT:
            case BLIND_VOTE_OP_RETURN_OUTPUT:
            case VOTE_REVEAL_UNLOCK_STAKE_OUTPUT:
            case VOTE_REVEAL_OP_RETURN_OUTPUT:
            case LOCKUP:
            case LOCKUP_OP_RETURN_OUTPUT:
            case UNLOCK:
                return true;
            case INVALID_OUTPUT:
                return false;
            default:
                return false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutputType(from MutableTxOutput) - Voting
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getUnspentBlindVoteStakeTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT);
    }

    public Set<TxOutput> getVoteRevealOpReturnTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutputType(from MutableTxOutput) - Issuance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getIssuanceCandidateTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Issuance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addIssuance(Issuance issuance) {
        state.addIssuance(issuance);
    }

    public Set<Issuance> getIssuanceSet() {
        return new HashSet<>(state.getIssuanceMap().values());
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
        if (state.getIssuanceMap().containsKey(txId))
            return Optional.of(state.getIssuanceMap().get(txId));
        else
            return Optional.empty();
    }

    public long getTotalIssuedAmount() {
        return getIssuanceCandidateTxOutputs().stream()
                .filter(txOutput -> isIssuanceTx(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bond
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO WIP

    // Lockup
    public Set<TxOutput> getLockupTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.LOCKUP);
    }

    public boolean isLockupOutput(TxOutput txOutput) {
        return getTxOutputType(txOutput) == TxOutputType.LOCKUP;
    }

    public Optional<TxOutput> getLockedTxOutput(String txId) {
        Optional<Tx> optionalTx = getTx(txId);
        return optionalTx.isPresent() ? optionalTx.get().getOutputs().stream()
                .filter(this::isLockupOutput)
                .findFirst() :
                Optional.empty();
    }

    // Unlock
    public boolean isUnlockOutput(TxOutput txOutput) {
        return getTxOutputType(txOutput) == TxOutputType.UNLOCK;
    }

    // LockTime
    public Optional<Integer> getLockTime(String txId) {
        int lockTime = getOptionalMutableTx(txId).map(MutableTx::getLockTime).orElse(-1);
        return lockTime < 0 ? Optional.empty() : Optional.of(lockTime);
    }

    //TODO sq: is that needed?
    public void removeLockTimeTxOutput(String txId) {
        getOptionalMutableTx(txId).ifPresent(mutableTx -> mutableTx.setLockTime(-1));
    }

    // UnlockBlockHeight
    public Optional<Integer> getUnlockBlockHeight(String txId) {
        int unLockBlockHeight = getOptionalMutableTx(txId).map(MutableTx::getUnlockBlockHeight).orElse(0);
        return unLockBlockHeight <= 0 ? Optional.empty() : Optional.of(unLockBlockHeight);
    }

    //TODO sq: is that needed?
    public void removeUnlockBlockHeightTxOutput(String txId) {
        getOptionalMutableTx(txId).ifPresent(mutableTx -> mutableTx.setUnlockBlockHeight(0));
    }

    public Set<TxOutput> getUnlockingTxOutputs() {
        return getMutableTxOutputStream()
                .filter(mutableTxOutput -> mutableTxOutput.getTxOutputType() == TxOutputType.UNLOCK)
                .filter(mutableTxOutput -> {
                    //TODO duplicate code logic to isTxOutputSpendable
                    //TODO use comparison to consensus?
                    return getUnlockBlockHeight(mutableTxOutput.getTxOutput().getTxId())
                            .filter(unLockBlockHeight -> getChainHeight() <= unLockBlockHeight)
                            .isPresent();
                })
                .map(MutableTxOutput::getTxOutput)
                .collect(Collectors.toSet());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Param
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO WIP
    public void setParamChangeMap(int chainHeight, ParamChangeMap paramChangeMap) {
        state.setParamChangeMap(chainHeight, paramChangeMap);
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SpentInfo
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSpentInfo(TxOutput.Key txOutputKey, SpentInfo spentInfo) {
        state.putSpentInfo(txOutputKey, spentInfo);
    }

    public Optional<SpentInfo> getSpentInfo(TxOutput txOutput) {
        return Optional.ofNullable(state.getSpentInfoMap().getOrDefault(txOutput.getKey(), null));
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

}


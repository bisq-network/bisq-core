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

import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputKey;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.ext.Issuance;
import bisq.core.dao.state.ext.Param;
import bisq.core.dao.state.ext.ParamChange;
import bisq.core.dao.state.period.Cycle;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
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

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class StateService {
    private final State state;

    private final List<BlockListener> blockListeners = new CopyOnWriteArrayList<>();
    private final List<ChainHeightListener> chainHeightListeners = new CopyOnWriteArrayList<>();
    private final List<ParseBlockChainListener> parseBlockChainListeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public StateService(State state) {
        super();

        this.state = state;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialize
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        state.setChainHeight(state.getGenesisBlockHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Parser sets blockHeight of new block to be parsed
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setNewBlockHeight(int blockHeight) {
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

        state.getUnspentTxOutputMap().clear();
        state.getUnspentTxOutputMap().putAll(snapshot.getUnspentTxOutputMap());

        state.getIssuanceMap().clear();
        state.getIssuanceMap().putAll(snapshot.getIssuanceMap());

        state.getSpentInfoMap().clear();
        state.getSpentInfoMap().putAll(snapshot.getSpentInfoMap());

        state.getParamChangeList().clear();
        state.getParamChangeList().addAll(snapshot.getParamChangeList());
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

    public Optional<Cycle> getCycle(int height) {
        return getCycles().stream()
                .filter(cycle -> cycle.getHeightOfFirstBlock() <= height)
                .filter(cycle -> cycle.getHeightOfLastBlock() >= height)
                .findAny();
    }

    public Optional<Integer> getStartHeightOfNextCycle(int blockHeight) {
        return getCycle(blockHeight).map(cycle -> cycle.getHeightOfLastBlock() + 1);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Block
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addNewBlock(Block block) {
        state.getBlocks().add(block);
        blockListeners.forEach(l -> l.onBlockAdded(block));
        log.info("New Block added at blockHeight " + block.getHeight());
    }

    public LinkedList<Block> getBlocks() {
        return state.getBlocks();
    }

    public Optional<Block> getLastBlock() {
        if (!getBlocks().isEmpty())
            return Optional.of(getBlocks().getLast());
        else
            return Optional.empty();
    }

    public int getBlockHeightOfLastBlock() {
        return getLastBlock().map(Block::getHeight).orElse(0);
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
        return State.getGenesisTotalSupply();
    }

    public Optional<Tx> getGenesisTx() {
        return getTx(getGenesisTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Stream<Tx> getTxStream() {
        return getBlocks().stream()
                .flatMap(block -> block.getTxs().stream());
    }

    private Map<String, Tx> getTxMap() {
        return getTxStream().collect(Collectors.toMap(Tx::getId, tx -> tx));
    }

    public Set<Tx> getTxs() {
        return getTxStream().collect(Collectors.toSet());
    }

    public Optional<Tx> getTx(String txId) {
        return Optional.ofNullable(getTxMap().get(txId));
    }

    public boolean containsTx(String txId) {
        return getTx(txId).isPresent();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxType
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxType> getOptionalTxType(String txId) {
        return getTx(txId).map(Tx::getTxType);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BurntFee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getBurntFee(String txId) {
        return getTx(txId).map(Tx::getBurntFee).orElse(0L);
    }

    public boolean hasTxBurntFee(String txId) {
        return getBurntFee(txId) > 0;
    }

    public long getTotalBurntFee() {
        return getTxStream()
                .mapToLong(Tx::getBurntFee)
                .sum();
    }

    public Set<Tx> getBurntFeeTxs() {
        return getTxStream()
                .filter(tx -> tx.getBurntFee() > 0)
                .collect(Collectors.toSet());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxInput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxOutput> getConnectedTxOutput(TxInput txInput) {
        return getTx(txInput.getConnectedTxOutputTxId())
                .map(tx -> tx.getTxOutputs().get(txInput.getConnectedTxOutputIndex()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Stream<TxOutput> getTxOutputStream() {
        return getTxStream()
                .flatMap(tx -> tx.getTxOutputs().stream());
    }

    public boolean existsTxOutput(TxOutputKey key) {
        return getTxOutputStream().anyMatch(txOutput -> txOutput.getKey().equals(key));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UnspentTxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Map<TxOutputKey, TxOutput> getUnspentTxOutputMap() {
        return state.getUnspentTxOutputMap();
    }

    public void addUnspentTxOutput(TxOutput txOutput) {
        getUnspentTxOutputMap().put(txOutput.getKey(), txOutput);
    }

    public void removeUnspentTxOutput(TxOutput txOutput) {
        getUnspentTxOutputMap().remove(txOutput.getKey());
    }

    public boolean isUnspent(TxOutputKey key) {
        return getUnspentTxOutputMap().containsKey(key);
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return new HashSet<>(getUnspentTxOutputMap().values());
    }

    public Optional<TxOutput> getUnspentTxOutput(TxOutputKey key) {
        return Optional.ofNullable(getUnspentTxOutputMap().getOrDefault(key, null));
    }

    public boolean isTxOutputSpendable(TxOutputKey key) {
        if (!isUnspent(key))
            return false;

        Optional<TxOutput> optionalTxOutput = getUnspentTxOutput(key);
        // The above isUnspent call satisfies optionalTxOutput.isPresent()
        checkArgument(optionalTxOutput.isPresent(), "optionalTxOutput must be present");
        TxOutput txOutput = optionalTxOutput.get();

        switch (txOutput.getTxOutputType()) {
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
                return isLockTimeOverForUnlockTxOutput(txOutput);
            case INVALID_OUTPUT:
                return false;
            default:
                return false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutputType
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getTxOutputsByTxOutputType(TxOutputType txOutputType) {
        return getTxOutputStream()
                .filter(txOutput -> txOutput.getTxOutputType() == txOutputType)
                .collect(Collectors.toSet());
    }

    public boolean isBsqTxOutputType(TxOutput txOutput) {
        final TxOutputType txOutputType = txOutput.getTxOutputType();
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
    // TxOutputType - Voting
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getUnspentBlindVoteStakeTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT).stream()
                .filter(txOutput -> isUnspent(txOutput.getKey()))
                .collect(Collectors.toSet());
    }

    public Set<TxOutput> getVoteRevealOpReturnTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutputType - Issuance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getIssuanceCandidateTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Issuance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addIssuance(Issuance issuance) {
        state.getIssuanceMap().put(issuance.getTxId(), issuance);
    }

    public Set<Issuance> getIssuanceSet() {
        return new HashSet<>(state.getIssuanceMap().values());
    }

    private Optional<Issuance> getIssuance(String txId) {
        if (state.getIssuanceMap().containsKey(txId))
            return Optional.of(state.getIssuanceMap().get(txId));
        else
            return Optional.empty();
    }

    public boolean isIssuanceTx(String txId) {
        return getIssuance(txId).isPresent();
    }

    public int getIssuanceBlockHeight(String txId) {
        return getIssuance(txId)
                .map(Issuance::getChainHeight)
                .orElse(0);
    }

    public long getTotalIssuedAmount() {
        return getIssuanceCandidateTxOutputs().stream()
                .filter(txOutput -> isIssuanceTx(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Non-BSQ
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addNonBsqTxOutput(TxOutput txOutput) {
        checkArgument(txOutput.getTxOutputType() == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT,
                "txOutput must be type ISSUANCE_CANDIDATE_OUTPUT");
        state.getNonBsqTxOutputMap().put(txOutput.getKey(), txOutput);
    }

    public Optional<TxOutput> getBtcTxOutput(TxOutputKey key) {
        // Issuance candidates which did not got accepted in voting are covered here
        Map<TxOutputKey, TxOutput> nonBsqTxOutputMap = state.getNonBsqTxOutputMap();
        if (nonBsqTxOutputMap.containsKey(key))
            return Optional.of(nonBsqTxOutputMap.get(key));

        // We might have also outputs of type BTC_OUTPUT
        return getTxOutputsByTxOutputType(TxOutputType.BTC_OUTPUT).stream()
                .filter(output -> output.getKey().equals(key))
                .findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bond
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Terminology
    // Lockup - txOutputs of LOCKUP type
    // Unlocking - UNLOCK txOutputs that are not yet spendable due to lock time
    // Unlocked - UNLOCK txOutputs that are spendable since the lock time has passed
    // LockTime - 0 means that the funds are spendable at the same block of the UNLOCK tx. For the user that is not
    // supported as we do not expose unconfirmed BSQ txs so lockTime of 1 is the smallest the use can actually use.

    // LockTime
    public Optional<Integer> getLockTime(String txId) {
        return getTx(txId).map(Tx::getLockTime);
    }

    // Lockup
    public boolean isLockupOutput(TxOutputKey key) {
        Optional<TxOutput> opTxOutput = getUnspentTxOutput(key);
        return opTxOutput.isPresent() && isLockupOutput(opTxOutput.get());
    }

    public boolean isLockupOutput(TxOutput txOutput) {
        return txOutput.getTxOutputType() == TxOutputType.LOCKUP;
    }

    public Set<TxOutput> getLockupTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.LOCKUP);
    }

    public Optional<TxOutput> getLockupTxOutput(String txId) {
        return getTx(txId).flatMap(tx -> tx.getTxOutputs().stream()
                .filter(this::isLockupOutput)
                .findFirst());
    }

    // Returns amount of all LOCKUP txOutputs (they might have been unlocking or unlocked in the meantime)
    public long getTotalAmountOfLockupTxOutputs() {
        return getLockupTxOutputs().stream()
                .mapToLong(TxOutput::getValue)
                .sum();
    }

    // Returns the current locked up amount (excluding unlocking and unlocked)
    public long getTotalLockupAmount() {
        return getTotalAmountOfLockupTxOutputs() - getTotalAmountOfUnLockingTxOutputs() - getTotalAmountOfUnLockedTxOutputs();
    }


    // Unlock
    private boolean isUnlockOutput(TxOutput txOutput) {
        return txOutput.getTxOutputType() == TxOutputType.UNLOCK;
    }

    // Unlocking
    // Return UNLOCK TxOutputs that are not yet spendable as lockTime is not over
    public Stream<TxOutput> getUnspentUnlockingTxOutputsStream() {
        return getTxOutputsByTxOutputType(TxOutputType.UNLOCK).stream()
                .filter(txOutput -> isUnspent(txOutput.getKey()))
                .filter(txOutput -> !isLockTimeOverForUnlockTxOutput(txOutput));
    }

    public long getTotalAmountOfUnLockingTxOutputs() {
        return getUnspentUnlockingTxOutputsStream()
                .mapToLong(TxOutput::getValue)
                .sum();
    }

    public boolean isUnlockingOutput(TxOutputKey key) {
        Optional<TxOutput> opTxOutput = getUnspentTxOutput(key);
        return opTxOutput.isPresent() && isUnlockingOutput(opTxOutput.get());
    }

    public boolean isUnlockingOutput(TxOutput txOutput) {
        return txOutput.getTxOutputType() != TxOutputType.UNLOCK ||
                isTxOutputSpendable(new TxOutputKey(txOutput.getTxId(), txOutput.getIndex()));
    }

    // Unlocked
    public Optional<Integer> getUnlockBlockHeight(String txId) {
        return getTx(txId).map(Tx::getUnlockBlockHeight);
    }

    private boolean isLockTimeOverForUnlockTxOutput(TxOutput unlockTxOutput) {
        checkArgument(isUnlockOutput(unlockTxOutput), "txOutput must be of type UNLOCK");
        return getUnlockBlockHeight(unlockTxOutput.getTxId())
                .map(unlockBlockHeight -> BondingConsensus.isLockTimeOver(unlockBlockHeight, getChainHeight()))
                .orElse(false);
    }

    // We don't care here about the unspent state
    public Stream<TxOutput> getUnlockedTxOutputsStream() {
        return getTxOutputsByTxOutputType(TxOutputType.UNLOCK).stream()
                .filter(this::isLockTimeOverForUnlockTxOutput);
    }

    public long getTotalAmountOfUnLockedTxOutputs() {
        return getUnlockedTxOutputsStream()
                .mapToLong(TxOutput::getValue)
                .sum();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Param
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setNewParam(int blockHeight, Param param, long paramValue) {
        List<ParamChange> paramChangeList = state.getParamChangeList();
        getStartHeightOfNextCycle(blockHeight)
                .ifPresent(heightOfNewCycle -> {
                    ParamChange paramChange = new ParamChange(param.name(), paramValue, heightOfNewCycle);
                    paramChangeList.add(paramChange);
                    // Addition with older height should not be possible but to ensure correct sorting lets run a sort.
                    paramChangeList.sort(Comparator.comparingInt(ParamChange::getActivationHeight));
                });
    }

    public long getParamValue(Param param, int blockHeight) {
        List<ParamChange> paramChangeList = new ArrayList<>(state.getParamChangeList());
        if (!paramChangeList.isEmpty()) {
            // List is sorted by height, we start from latest entries to find most recent entry.
            for (int i = paramChangeList.size() - 1; i >= 0; i--) {
                ParamChange paramChange = paramChangeList.get(i);
                if (paramChange.getParamName().equals(param.name()) &&
                        blockHeight >= paramChange.getActivationHeight()) {
                    return paramChange.getValue();
                }
            }
        }

        // If no value found we use default values
        return param.getDefaultValue();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SpentInfo
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSpentInfo(TxOutputKey txOutputKey, SpentInfo spentInfo) {
        state.getSpentInfoMap().put(txOutputKey, spentInfo);
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


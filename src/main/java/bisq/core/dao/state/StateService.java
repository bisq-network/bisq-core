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

import bisq.core.dao.period.Cycle;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxBlock;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.events.BlindVoteEvent;
import bisq.core.dao.state.events.ParamChangeEvent;
import bisq.core.dao.state.events.StateChangeEvent;
import bisq.core.dao.voting.blindvote.BlindVote;
import bisq.core.dao.voting.proposal.param.ParamChange;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.collect.ImmutableSet;

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
    private State state;

    private List<StateChangeEventsProvider> stateChangeEventsProviders = new CopyOnWriteArrayList<>();
    private List<BlockListener> blockListeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public StateService(State state) {
        super();

        this.state = state;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateChangeEventsProvider
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void registerStateChangeEventsProvider(StateChangeEventsProvider stateChangeEventsProvider) {
        stateChangeEventsProviders.add(stateChangeEventsProvider);
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Snapshot
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO
    public void applySnapshot(State snapshot) {
        this.getBlocks().clear();
        this.getBlocks().addAll(snapshot.getBlocks());

        getUnspentTxOutputMap().clear();
        getUnspentTxOutputMap().putAll(snapshot.getUnspentTxOutputMap());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Modify state
    ///////////////////////////////////////////////////////////////////////////////////////////

    // After parsing of a new txBlock is complete we trigger the processing of any non blockchain data like
    // proposalPayloads or blindVotes and after we have collected all stateChangeEvents we create a Block and
    // notify the listeners.
    public void applyTxBlock(TxBlock txBlock) {
        // Those who registered to process a txBlock might return a list of StateChangeEvents.
        // We collect all from all providers and then go on.
        Set<StateChangeEvent> stateChangeEvents = new HashSet<>();
        stateChangeEventsProviders.forEach(stateChangeEventsProvider -> {
            stateChangeEvents.addAll(stateChangeEventsProvider.provideStateChangeEvents(txBlock));
        });

        // Now we have both the immutable txBlock and the collected stateChangeEvents.
        // We now add the immutable Block containing both data.
        final Block block = new Block(txBlock, ImmutableSet.copyOf(stateChangeEvents));
        state.addBlock(block);

        blockListeners.forEach(l -> l.onBlockAdded(block));

        log.info("New block added at blockHeight " + block.getHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addCycle(Cycle cycle) {
        state.addCycle(cycle);
    }


    public List<TxBlock> getClonedTxBlocksFrom(int fromBlockHeight) {
        final LinkedList<TxBlock> clonedTxBlocks = new LinkedList<>(getTxBlocks());
        return clonedTxBlocks.stream()
                .filter(block -> block.getHeight() >= fromBlockHeight)
                .collect(Collectors.toList());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTxType(String txId, TxType txType) {
        state.putTxType(txId, txType);
    }


    public void setBurntFee(String txId, long burnedFee) {
        state.putBurntFee(txId, burnedFee);
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

    public void addIssuanceTxOutput(TxOutput txOutput, int chainHeight) {
        state.putIssuanceBlockHeight(txOutput, chainHeight);
        addUnspentTxOutput(txOutput);
    }

    public void setSpentInfo(TxOutput txOutput, int blockHeight, String txId, int inputIndex) {
        state.putSpentInfo(txOutput, blockHeight, txId, inputIndex);
    }

    public void setTxOutputType(TxOutput txOutput, TxOutputType txOutputType) {
        state.putTxOutputType(txOutput, txOutputType);
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

    public LinkedList<TxBlock> getTxBlockFromState(State state) {
        return new LinkedList<>(state.getBlocks()).stream()
                .map(Block::getTxBlock)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    // Tx
    public Map<String, TxType> getTxTypeMap() {
        return state.getTxTypeMap();
    }

    public Map<String, Long> getBurntFeeMap() {
        return state.getBurntFeeMap();
    }

    public Map<String, Integer> getIssuanceBlockHeightMap() {
        return state.getIssuanceBlockHeightMap();
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
    public List<Cycle> getCycles() {
        return state.getCycles();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Block
    ///////////////////////////////////////////////////////////////////////////////////////////


    private Optional<Block> getBlockAtHeight(int height) {
        return getBlocks().stream()
                .filter(block -> block.getHeight() == height)
                .findAny();
    }


    public LinkedList<TxBlock> getTxBlocks(LinkedList<Block> blocks) {
        return new LinkedList<>(blocks).stream()
                .map(Block::getTxBlock)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    public LinkedList<TxBlock> getTxBlocks() {
        return getTxBlocks(this.getBlocks());
    }

    public boolean containsTxBlock(TxBlock txBlock) {
        return getTxBlocks().contains(txBlock);
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
        return getIssuanceBlockHeightMap().containsKey(txId);
    }

    public int getIssuanceBlockHeight(String txId) {
        return getIssuanceBlockHeightMap().getOrDefault(txId, 0);
    }

    public Optional<Tx> getTx(String txId) {
        return Optional.ofNullable(getTxMap().get(txId));
    }

    public Map<String, Tx> getTxMap() {
        return getTxBlocks().stream()
                .flatMap(txBlock -> txBlock.getTxs().stream())
                .collect(Collectors.toMap(Tx::getId, tx -> tx));
    }

    public Set<Tx> getTxs() {
        return getTxBlocks().stream()
                .flatMap(txBlock -> txBlock.getTxs().stream())
                .collect(Collectors.toSet());
    }

    public Set<Tx> getFeeTxs() {
        return getTxBlocks().stream()
                .flatMap(txBlock -> txBlock.getTxs().stream())
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
        return txOutputType != TxOutputType.UNDEFINED &&
                txOutputType != TxOutputType.BTC_OUTPUT &&
                txOutputType != TxOutputType.INVALID_OUTPUT;
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateChangeEvent
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Block getLastBlock() {
        return this.getBlocks().getLast();
    }

    public Set<StateChangeEvent> getStateChangeEvents() {
        return this.getBlocks().stream()
                .flatMap(block -> block.getStateChangeEvents().stream())
                .collect(Collectors.toSet());
    }

    public Set<ParamChangeEvent> getAddChangeParamEvents() {
        return getStateChangeEvents().stream()
                .filter(event -> event instanceof ParamChangeEvent)
                .map(event -> (ParamChangeEvent) event)
                .collect(Collectors.toSet());
    }

    public Set<BlindVoteEvent> getAddBlindVoteEvents() {
        return getStateChangeEvents().stream()
                .filter(event -> event instanceof BlindVoteEvent)
                .map(event -> (BlindVoteEvent) event)
                .collect(Collectors.toSet());
    }

    public Set<BlindVote> getBlindVotes() {
        return getAddBlindVoteEvents().stream()
                .map(BlindVoteEvent::getBlindVote)
                .collect(Collectors.toSet());
    }

    public Set<ParamChange> getParamChanges() {
        return getAddChangeParamEvents().stream()
                .map(ParamChangeEvent::getParamChange)
                .collect(Collectors.toSet());
    }
}


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

package bisq.core.dao.consensus.state;

import bisq.core.dao.consensus.period.Cycle;
import bisq.core.dao.consensus.state.blockchain.SpentInfo;
import bisq.core.dao.consensus.state.blockchain.TxBlock;
import bisq.core.dao.consensus.state.blockchain.TxOutput;
import bisq.core.dao.consensus.state.blockchain.TxOutputType;
import bisq.core.dao.consensus.state.blockchain.TxType;
import bisq.core.dao.consensus.state.events.StateChangeEvent;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Write and read access to state for client running in parser thread.
 */
@Slf4j
public class StateService extends BaseStateService {
    private State state;

    private List<StateChangeEventsProvider> stateChangeEventsProviders = new CopyOnWriteArrayList<>();


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
        // The providers are called in the parser thread, so we have a single threaded execution model here.
        Set<StateChangeEvent> stateChangeEvents = new HashSet<>();
        stateChangeEventsProviders.forEach(stateChangeEventsProvider -> {
            stateChangeEvents.addAll(stateChangeEventsProvider.provideStateChangeEvents(txBlock));
        });

        // Now we have both the immutable txBlock and the collected stateChangeEvents.
        // We now add the immutable Block containing both data.
        final Block block = new Block(txBlock, ImmutableSet.copyOf(stateChangeEvents));
        state.addBlock(block);

        log.info("New block added at blockHeight " + block.getHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addCycle(Cycle cycle) {
        state.addCycle(cycle);
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

    public void addIssuanceTxOutput(TxOutput txOutput) {
        state.putIssuanceBlockHeight(txOutput, getChainHeight());
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
    @Override
    public String getGenesisTxId() {
        return state.getGenesisTxId();
    }

    @Override
    public int getGenesisBlockHeight() {
        return state.getGenesisBlockHeight();
    }

    @Override
    public Coin getGenesisTotalSupply() {
        return state.getGenesisTotalSupply();
    }

    // Block
    @Override
    public LinkedList<Block> getBlocks() {
        return state.getBlocks();
    }

    public LinkedList<TxBlock> getTxBlockFromState(State state) {
        return new LinkedList<>(state.getBlocks()).stream()
                .map(Block::getTxBlock)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    // Tx
    @Override
    public Map<String, TxType> getTxTypeMap() {
        return state.getTxTypeMap();
    }

    @Override
    public Map<String, Long> getBurntFeeMap() {
        return state.getBurntFeeMap();
    }

    @Override
    public Map<String, Integer> getIssuanceBlockHeightMap() {
        return state.getIssuanceBlockHeightMap();
    }

    // TxOutput
    @Override
    public Map<TxOutput.Key, TxOutput> getUnspentTxOutputMap() {
        return state.getUnspentTxOutputMap();
    }

    @Override
    public Map<TxOutput.Key, SpentInfo> getTxOutputSpentInfoMap() {
        return state.getSpentInfoMap();
    }

    @Override
    public Map<TxOutput.Key, TxOutputType> getTxOutputTypeMap() {
        return state.getTxOutputTypeMap();
    }

    // Cycle
    @Override
    public List<Cycle> getCycles() {
        return state.getCycles();
    }
}


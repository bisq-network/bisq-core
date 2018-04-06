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

package bisq.core.dao.vote;

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.param.DaoParamService;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provide information about the phase and cycle of the request/voting cycle.
 * A cycle is the sequence of distinct phases. The first cycle and phase starts with the genesis block height.
 * All time events are measured in blocks.
 * The index of first cycle is 1 not 0! The index of first block in first phase is 0 (genesis height).
 */
@Slf4j
public class PeriodService implements BsqBlockChain.Listener {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final int genesisBlockHeight;
    @Getter
    private ObjectProperty<Cycles.Phase> phaseProperty = new SimpleObjectProperty<>(Cycles.Phase.UNDEFINED);
    private Cycles cycles;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodService(ReadableBsqBlockChain readableBsqBlockChain,
                         @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight,
                         DaoParamService daoParamService) {
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.genesisBlockHeight = genesisBlockHeight;
        this.cycles = new Cycles(genesisBlockHeight, daoParamService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {
    }

    public void onAllServicesInitialized() {
        readableBsqBlockChain.addListener(this);
        onChainHeightChanged(readableBsqBlockChain.getChainHeadHeight());
    }

    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        onChainHeightChanged(bsqBlock.getHeight());
    }

    public Cycles.Phase getPhase(int blockHeight) {
        return cycles.getPhase(blockHeight);
    }

    public Cycles.Cycle getCycle(int blockHeight) {
        return cycles.getCycle(blockHeight);
    }

    public boolean isInPhase(int blockHeight, Cycles.Phase phase) {
        return getPhase(blockHeight) == phase;
    }

    // If we are not in the parsing, it is safe to call it without explicit chainHeadHeight
    public boolean isTxInPhase(String txId, Cycles.Phase phase) {
        Tx tx = readableBsqBlockChain.getTxMap().get(txId);
        return tx != null && isInPhase(tx.getBlockHeight(), phase);
    }

    public boolean isTxInCurrentCycle(String txId) {
        Tx tx = readableBsqBlockChain.getTxMap().get(txId);
        return tx != null && getCycle(tx.getBlockHeight()) == getCycle(readableBsqBlockChain.getChainHeadHeight());
    }

    public boolean isTxInPastCycle(String txId) {
        Tx tx = readableBsqBlockChain.getTxMap().get(txId);
        return tx != null && getCycle(tx.getBlockHeight()).getStartBlock() <
                getCycle(readableBsqBlockChain.getChainHeadHeight()).getStartBlock();
    }

    public int getNumOfStartedCycles() {
        return cycles.getNumberOfStartedCycles();
    }

    // Not used yet be leave it
    public int getNumOfCompletedCycles(int chainHeight) {
        Cycles.Cycle lastCycle = cycles.getCycle(chainHeight);
        if (lastCycle.getStartBlock() + lastCycle.getCycleDuration() == chainHeight)
            return cycles.getNumberOfStartedCycles();
        return cycles.getNumberOfStartedCycles() - 1;
    }

    public Cycles.Phase getPhaseForHeight(int chainHeight) {
        return getPhase(chainHeight);
    }

    public int getAbsoluteStartBlockOfPhase(int chainHeight, Cycles.Phase phase) {
        return cycles.getStartBlockOfPhase(chainHeight, phase);
    }

    public int getAbsoluteEndBlockOfPhase(int chainHeight, Cycles.Phase phase) {
        return cycles.getEndBlockOfPhase(chainHeight, phase);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onChainHeightChanged(int chainHeight) {
        cycles.onChainHeightChanged(chainHeight);
        phaseProperty.set(getPhase(chainHeight));
    }

}

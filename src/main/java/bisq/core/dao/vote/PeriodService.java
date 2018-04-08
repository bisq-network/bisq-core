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
import bisq.core.dao.param.DaoParam;
import bisq.core.dao.param.DaoParamService;

import com.google.inject.Inject;

import javax.inject.Named;

import com.google.common.annotations.VisibleForTesting;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    // phase period: 30 days + 30 blocks
    public enum Phase {
        UNDEFINED, // Must be exactly 0 blocks
        PROPOSAL,
        BREAK1,
        BLIND_VOTE,
        BREAK2,
        VOTE_REVEAL,
        BREAK3,
        ISSUANCE, // Must be exactly 1 block
        BREAK4;
    }

    // The default cycle is used as the pre genesis cycle
    public class Cycle {
        // Durations of each phase
        @Getter
        List<Integer> phases = new ArrayList<>();

        @Getter
        private int startBlock = 0;
        @Getter
        private int lastBlock = 0;

        Cycle(int startBlock, DaoParamService daoParamService) {
            this.startBlock = startBlock;
            Arrays.asList(PeriodService.Phase.values()).stream().forEach(phase -> {
                String name = "PHASE_" + phase.name();
                phases.add((int) daoParamService.getDaoParamValue(DaoParam.valueOf(name), startBlock));
            });
            this.lastBlock = startBlock + getCycleDuration() - 1;
        }

        public int getCycleDuration() {
            return getPhases().stream().mapToInt(Integer::intValue).sum();
        }

        public int getPhaseDuration(PeriodService.Phase phase) {
            return getPhases().get(phase.ordinal());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final ReadableBsqBlockChain readableBsqBlockChain;
    @Getter
    private ObjectProperty<Phase> phaseProperty = new SimpleObjectProperty<>(Phase.UNDEFINED);
    private List<Cycle> cycles = new ArrayList<>();
    private DaoParamService daoParamService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodService(ReadableBsqBlockChain readableBsqBlockChain,
                         @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight,
                         DaoParamService daoParamService) {
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.daoParamService = daoParamService;

        // Initialize genesis cycle
        this.cycles.add(new Cycle(genesisBlockHeight, daoParamService));
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

    public PeriodService.Phase getPhase(int blockHeight) {
        Cycle cycle = getCycle(blockHeight);
        if (cycle == null) return Phase.UNDEFINED;
        int checkHeight = cycle.getStartBlock();
        List<Integer> phases = cycle.getPhases();
        for (int i = 0; i < phases.size(); ++i) {
            if (checkHeight + phases.get(i) > blockHeight)
                return PeriodService.Phase.values()[i];
            checkHeight += phases.get(i);
        }
        return PeriodService.Phase.UNDEFINED;
    }

    // Return null for blockHeight < genesis height, it should never happen
    public Cycle getCycle(int blockHeight) {
        Optional<Cycle> cycle = cycles.stream().
                filter(c -> c.getStartBlock() <= blockHeight && blockHeight <= c.getLastBlock()).findAny();
        if (cycle.isPresent())
            return cycle.get();
        return null;
    }

    public boolean isInPhase(int blockHeight, Phase phase) {
        return getPhase(blockHeight) == phase;
    }

    // If we are not in the parsing, it is safe to call it without explicit chainHeadHeight
    public boolean isTxInPhase(String txId, Phase phase) {
        Tx tx = readableBsqBlockChain.getTxMap().get(txId);
        return tx != null && isInPhase(tx.getBlockHeight(), phase);
    }

    public boolean isTxInCorrectCycle(int txHeight, int chainHeadHeight) {
        return getCycle(txHeight) == getCycle(chainHeadHeight);
    }

    public boolean isTxInCorrectCycle(String txId, int chainHeadHeight) {
        Tx tx = readableBsqBlockChain.getTxMap().get(txId);
        return tx != null && isTxInCorrectCycle(tx.getBlockHeight(), chainHeadHeight);
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

    public boolean isTxInPastCycle(Tx tx, int chainHeadHeight) {
        return getCycle(tx.getBlockHeight()).startBlock < getCycle(chainHeadHeight).startBlock;
    }

    public boolean isTxInPastCycle(String txId, int chainHeadHeight) {
        return readableBsqBlockChain.getTx(txId).filter(tx -> isTxInPastCycle(tx, chainHeadHeight)).isPresent();
    }

    public int getNumberOfStartedCycles() {
        // There is one dummy cycle added before the genesis cycle
        return cycles.size();
    }

//    public int getNumOfCompletedCycles(int chainHeight) {
//        Cycle lastCycle = getCycle(chainHeight);
//        if (lastCycle.getStartBlock() + lastCycle.getCycleDuration() == chainHeight)
//            return getNumberOfStartedCycles();
//        return getNumberOfStartedCycles() - 1;
//    }

    // Get first block of phase within the given cycle
    public int getFirstBlockOfPhase(Cycle cycle, Phase phase) {
        int checkHeight = cycle.getStartBlock();
        List<Integer> phases = cycle.getPhases();
        for (int i = 0; i < phases.size(); ++i) {
            if (i == phase.ordinal()) {
                return checkHeight;
            }
            checkHeight += phases.get(i);
        }
        // Can't handle future phases until cycle is defined
        return 0;
    }

    // Get last block of phase within the given cycle
    public int getLastBlockOfPhase(Cycle cycle, Phase phase) {
        int checkHeight = cycle.getStartBlock();
        List<Integer> phases = cycle.getPhases();
        for (int i = 0; i < phases.size(); ++i) {
            if (i == phase.ordinal()) {
                return checkHeight + phases.get(i) - 1;
            }
            checkHeight += phases.get(i);
        }
        // Can't handle future phases until cycle is defined
        return 0;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    void onChainHeightChanged(int chainHeight) {
        Cycle lastCycle = cycles.get(cycles.size() - 1);
        while (chainHeight > lastCycle.getLastBlock()) {
            cycles.add(new Cycle(lastCycle.getLastBlock(), daoParamService));
            lastCycle = cycles.get(cycles.size() - 1);
        }
        phaseProperty.set(getPhase(chainHeight));
    }
}

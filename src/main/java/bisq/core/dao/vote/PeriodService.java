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
import bisq.core.dao.state.Block;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.events.AddChangeParamEvent;
import bisq.core.dao.state.events.StateChangeEvent;
import bisq.core.dao.vote.proposal.param.ChangeParamPayload;
import bisq.core.dao.vote.proposal.param.Param;

import bisq.common.app.DevEnv;

import com.google.inject.Inject;

import javax.inject.Named;

import com.google.common.annotations.VisibleForTesting;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provide information about the phase and cycle of the request/voting cycle.
 * A cycle is the sequence of distinct phases. The first cycle and phase starts with the genesis block height.
 * All time events are measured in blocks.
 * The index of first cycle is 1 not 0! The index of first block in first phase is 0 (genesis height).
 */
@Slf4j
public class PeriodService {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    // phase period: 30 days + 30 blocks
    public enum Phase {
        // TODO for testing
        UNDEFINED(0),
        PROPOSAL(2),
        BREAK1(1),
        BLIND_VOTE(2),
        BREAK2(1),
        VOTE_REVEAL(2),
        BREAK3(1),
        ISSUANCE(1), // Must have only 1 block!
        BREAK4(1);

      /* UNDEFINED(0),
        COMPENSATION_REQUESTS(144 * 23),
        BREAK1(10),
        BLIND_VOTE(144 * 4),
        BREAK2(10),
        VOTE_REVEAL(144 * 3),
        BREAK3(10);*/

        /**
         * 144 blocks is 1 day if a block is found each 10 min.
         */
        @Getter
        private int durationInBlocks;

        Phase(int durationInBlocks) {
            this.durationInBlocks = durationInBlocks;
        }
    }

    public static class Phases extends HashMap<Phase, Integer> {
        public Phase getPhase() {
            return phase;
        }

        public void setPhase(Phase phase) {
            this.phase = phase;
        }

        public void setDurationInBlocks(int durationInBlocks) {
            this.durationInBlocks = durationInBlocks;
        }

        public int getDurationInBlocks() {
            return durationInBlocks;
        }

        private Phase phase;
        private int durationInBlocks;

        public Phases() {
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final StateService stateService;
    private final int genesisBlockHeight;
    @Getter
    private ObjectProperty<Phase> phaseProperty = new SimpleObjectProperty<>(Phase.UNDEFINED);
    //  private Phases phases = new Phases();
    private Map<Phase, Integer> phases = new HashMap<>();
    private int chainHeight;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodService(StateService stateService,
                         @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this.stateService = stateService;
        this.genesisBlockHeight = genesisBlockHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        // At genesis height we add the default values from the Param enum to our StateChangeEvent list.
        stateService.registerStateChangeEventsProvider(txBlock -> {
            Set<StateChangeEvent> stateChangeEvents = new HashSet<>();
            final int height = txBlock.getHeight();
            if (height == stateService.getGenesisBlockHeight()) {
                Arrays.asList(Phase.values())
                        .forEach(phase -> initWithDefaultValueAtGenesisHeight(phase, height)
                                .ifPresent(stateChangeEvents::add));
            }
            return stateChangeEvents;
        });

        stateService.addBlockListener(new StateService.BlockListener() {
            @Override
            public boolean executeOnUserThread() {
                return false;
            }

            @Override
            public void onBlockAdded(Block block) {
                chainHeight = block.getHeight();

                // At genesis we want to get triggered the update. Afterwards we only apply changes at the last
                // block in a cycle.
                if (chainHeight == stateService.getGenesisBlockHeight() || isLastBlockInCycle(chainHeight)) {
                    processChangeParamPayloads();
                }

                final int relativeBlocksInCycle = getRelativeBlocksInCycle(genesisBlockHeight, chainHeight, getNumBlocksOfCycle());
                phaseProperty.set(calculatePhase(relativeBlocksInCycle));
            }
        });
    }

    private void processChangeParamPayloads() {
        stateService.getChangeParamPayloads().forEach(this::processChangeParamPayload);
        stateService.setPhaseValueMap(phases);
    }

    private void processChangeParamPayload(ChangeParamPayload changeParamPayload) {
        final String paramName = changeParamPayload.getParam().name();
        if (paramName.startsWith("PHASE_")) {
            final String phaseName = paramName.replace("PHASE_", "");
            final Phase phase = Phase.valueOf(phaseName);
            phases.put(phase, (int) changeParamPayload.getValue());
        }
    }

    private boolean isLastBlockInCycle(int height) {
        return height == getAbsoluteEndBlockOfPhase(height, Phase.BREAK4);
    }

    private Optional<AddChangeParamEvent> initWithDefaultValueAtGenesisHeight(Phase phase, int height) {
        return Arrays.stream(Param.values())
                .filter(param -> isParamMatchingPhase(param, phase))
                .map(param -> new ChangeParamPayload(param, param.getDefaultValue()))
                .map(changeParamPayload -> new AddChangeParamEvent(changeParamPayload, height))
                .findAny();
    }

    private boolean isParamMatchingPhase(Param param, Phase phase) {
        return param.name().replace("PHASE_", "").equals(phase.name());
    }


    public int getDurationInBlocks(Phase phase) {
        return 1;
    }


    public boolean isInPhase(int blockHeight, Phase phase) {
        int start = getBlockUntilPhaseStart(phase);
        int end = getBlockUntilPhaseEnd(phase);
        int numBlocksOfTxHeightSinceGenesis = blockHeight - genesisBlockHeight;
        int heightInCycle = numBlocksOfTxHeightSinceGenesis % getNumBlocksOfCycle();
        return heightInCycle >= start && heightInCycle <= end;
    }

    // If we are not in the parsing, it is safe to call it without explicit chainHeadHeight
    public boolean isTxInPhase(String txId, Phase phase) {
        Tx tx = stateService.getTxMap().get(txId);
        return tx != null && isInPhase(tx.getBlockHeight(), phase);
    }

    public boolean isTxInCorrectCycle(int txHeight, int chainHeadHeight) {
        return isTxInCorrectCycle(txHeight,
                chainHeadHeight,
                genesisBlockHeight,
                getNumBlocksOfCycle());
    }

    public boolean isTxInCorrectCycle(String txId, int chainHeadHeight) {
        return stateService.getTx(txId)
                .filter(tx -> isTxInCorrectCycle(tx.getBlockHeight(), chainHeadHeight))
                .isPresent();
    }

    public boolean isTxInPastCycle(String txId, int chainHeadHeight) {
        return stateService.getTx(txId).filter(tx -> isTxInPastCycle(tx, chainHeadHeight)).isPresent();
    }

    public boolean isTxInPastCycle(Tx tx, int chainHeadHeight) {
        return isTxInPastCycle(tx.getBlockHeight(),
                chainHeadHeight,
                genesisBlockHeight,
                getNumBlocksOfCycle());
    }

    public int getNumOfStartedCycles(int chainHeight) {
        return getNumOfStartedCycles(chainHeight,
                genesisBlockHeight,
                getNumBlocksOfCycle());
    }

    // Not used yet be leave it
    public int getNumOfCompletedCycles(int chainHeight) {
        return getNumOfCompletedCycles(chainHeight,
                genesisBlockHeight,
                getNumBlocksOfCycle());
    }

    public Phase getPhaseForHeight(int chainHeight) {
        int startOfCurrentCycle = getAbsoluteStartBlockOfCycle(chainHeight, genesisBlockHeight, getNumBlocksOfCycle());
        int offset = chainHeight - startOfCurrentCycle;
        return calculatePhase(offset);
    }

    public int getAbsoluteStartBlockOfPhase(int chainHeight, Phase phase) {
        return getAbsoluteStartBlockOfPhase(chainHeight,
                genesisBlockHeight,
                phase,
                getNumBlocksOfCycle());
    }

    public int getAbsoluteEndBlockOfPhase(int chainHeight, Phase phase) {
        return getAbsoluteEndBlockOfPhase(chainHeight,
                genesisBlockHeight,
                phase,
                getNumBlocksOfCycle());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    @VisibleForTesting
    int getRelativeBlocksInCycle(int genesisHeight, int bestChainHeight, int numBlocksOfCycle) {
        return (bestChainHeight - genesisHeight) % numBlocksOfCycle;
    }

    int getBlockUntilPhaseStart(Phase target) {
        int totalDuration = 0;
        for (Phase phase : Arrays.asList(Phase.values())) {
            if (phase == target)
                break;
            else
                totalDuration += phase.getDurationInBlocks();
        }
        return totalDuration;
    }

    int getBlockUntilPhaseEnd(Phase target) {
        int totalDuration = 0;
        for (Phase phase : Arrays.asList(Phase.values())) {
            totalDuration += phase.getDurationInBlocks();
            if (phase == target)
                break;
        }
        return totalDuration;
    }

    @VisibleForTesting
    Phase calculatePhase(int blocksInNewPhase) {
        if (blocksInNewPhase < Phase.PROPOSAL.getDurationInBlocks())
            return Phase.PROPOSAL;
        else if (blocksInNewPhase < Phase.PROPOSAL.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks())
            return Phase.BREAK1;
        else if (blocksInNewPhase < Phase.PROPOSAL.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks() +
                Phase.BLIND_VOTE.getDurationInBlocks())
            return Phase.BLIND_VOTE;
        else if (blocksInNewPhase < Phase.PROPOSAL.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks() +
                Phase.BLIND_VOTE.getDurationInBlocks() +
                Phase.BREAK2.getDurationInBlocks())
            return Phase.BREAK2;
        else if (blocksInNewPhase < Phase.PROPOSAL.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks() +
                Phase.BLIND_VOTE.getDurationInBlocks() +
                Phase.BREAK2.getDurationInBlocks() +
                Phase.VOTE_REVEAL.getDurationInBlocks())
            return Phase.VOTE_REVEAL;
        else if (blocksInNewPhase < Phase.PROPOSAL.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks() +
                Phase.BLIND_VOTE.getDurationInBlocks() +
                Phase.BREAK2.getDurationInBlocks() +
                Phase.VOTE_REVEAL.getDurationInBlocks() +
                Phase.BREAK3.getDurationInBlocks())
            return Phase.BREAK3;
        else if (blocksInNewPhase < Phase.PROPOSAL.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks() +
                Phase.BLIND_VOTE.getDurationInBlocks() +
                Phase.BREAK2.getDurationInBlocks() +
                Phase.VOTE_REVEAL.getDurationInBlocks() +
                Phase.BREAK3.getDurationInBlocks() +
                Phase.ISSUANCE.getDurationInBlocks())
            return Phase.ISSUANCE;
        else if (blocksInNewPhase < Phase.PROPOSAL.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks() +
                Phase.BLIND_VOTE.getDurationInBlocks() +
                Phase.BREAK2.getDurationInBlocks() +
                Phase.VOTE_REVEAL.getDurationInBlocks() +
                Phase.BREAK3.getDurationInBlocks() +
                Phase.ISSUANCE.getDurationInBlocks() +
                Phase.BREAK4.getDurationInBlocks())
            return Phase.BREAK4;
        else {
            log.error("blocksInNewPhase is not covered by phase checks. blocksInNewPhase={}", blocksInNewPhase);
            if (DevEnv.isDevMode())
                throw new RuntimeException("blocksInNewPhase is not covered by phase checks. blocksInNewPhase=" + blocksInNewPhase);
            else
                return Phase.UNDEFINED;
        }
    }

    @VisibleForTesting
    boolean isTxInCorrectCycle(int txHeight, int chainHeight, int genesisHeight, int numBlocksOfCycle) {
        final int numOfCompletedCycles = getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle);
        final int blockAtCycleStart = genesisHeight + numOfCompletedCycles * numBlocksOfCycle;
        final int blockAtCycleEnd = blockAtCycleStart + numBlocksOfCycle - 1;
        return txHeight <= chainHeight &&
                chainHeight >= genesisHeight &&
                txHeight >= blockAtCycleStart &&
                txHeight <= blockAtCycleEnd;
    }

    @VisibleForTesting
    boolean isTxInPastCycle(int txHeight, int chainHeight, int genesisHeight, int numBlocksOfCycle) {
        final int numOfCompletedCycles = getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle);
        final int blockAtCycleStart = genesisHeight + numOfCompletedCycles * numBlocksOfCycle;
        return txHeight <= chainHeight &&
                chainHeight >= genesisHeight &&
                txHeight <= blockAtCycleStart;
    }

    @VisibleForTesting
    int getAbsoluteStartBlockOfPhase(int chainHeight, int genesisHeight, Phase phase, int numBlocksOfCycle) {
        return genesisHeight + getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle) * getNumBlocksOfCycle() + getNumBlocksOfPhaseStart(phase);
    }

    @VisibleForTesting
    int getAbsoluteStartBlockOfCycle(int chainHeight, int genesisHeight, int numBlocksOfCycle) {
        return genesisHeight + getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle) * getNumBlocksOfCycle() + getNumBlocksOfPhaseStart(Phase.PROPOSAL);
    }

    @VisibleForTesting
    int getAbsoluteEndBlockOfPhase(int chainHeight, int genesisHeight, Phase phase, int numBlocksOfCycle) {
        return getAbsoluteStartBlockOfPhase(chainHeight, genesisHeight, phase, numBlocksOfCycle) + phase.getDurationInBlocks() - 1;
    }


    @VisibleForTesting
    int getNumOfStartedCycles(int chainHeight, int genesisHeight, int numBlocksOfCycle) {
        if (chainHeight >= genesisHeight)
            return getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle) + 1;
        else
            return 0;
    }

    int getNumOfCompletedCycles(int chainHeight, int genesisHeight, int numBlocksOfCycle) {
        if (chainHeight >= genesisHeight && numBlocksOfCycle > 0)
            return (chainHeight - genesisHeight) / numBlocksOfCycle;
        else
            return 0;
    }

    @VisibleForTesting
    int getNumBlocksOfPhaseStart(Phase phase) {
        int blocks = 0;
        for (int i = 0; i < Phase.values().length; i++) {
            final Phase currentPhase = Phase.values()[i];
            if (currentPhase == phase)
                break;

            blocks += currentPhase.getDurationInBlocks();
        }
        return blocks;
    }

    @VisibleForTesting
    int getNumBlocksOfCycle() {
        int blocks = 0;
        for (int i = 0; i < Phase.values().length; i++) {
            blocks += Phase.values()[i].getDurationInBlocks();
        }
        return blocks;
    }
}

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

import bisq.core.dao.state.Block;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.TxBlock;
import bisq.core.dao.state.events.AddChangeParamEvent;
import bisq.core.dao.state.events.StateChangeEvent;
import bisq.core.dao.vote.proposal.param.ChangeParamPayload;
import bisq.core.dao.vote.proposal.param.Param;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provide information about the phase and cycle of the monthly dao cycle for proposals and voting.
 * A cycle is the sequence of distinct phases. The first cycle and phase starts with the genesis block height.
 * All time events are measured in blocks.
 *
 * Executes in the parser thread.
 */
@Slf4j
public class PeriodService {

    private final StateService stateService;
    @Getter
    private Cycle currentCycle;
    @Getter
    private int chainHeight;
    private final List<Cycle> cycles = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodService(StateService stateService) {
        this.stateService = stateService;

        // We want to have the initial data set up before the genesis tx gets parsed so we do it here in the constructor
        // as onAllServicesInitialized might get called after the parser has started.
        // We add the default values from the Param enum to our StateChangeEvent list.
        Cycle cycle = new Cycle(stateService.getGenesisBlockHeight());
        Arrays.asList(Phase.values()).forEach(phase -> {
            final Optional<AddChangeParamEvent> optionalEvent = initWithDefaultValueAtGenesisHeight(phase, stateService.getGenesisBlockHeight());
            optionalEvent.ifPresent(event -> applyParamToPhasesInCycle(event.getChangeParamPayload(), cycle));
        });
        currentCycle = cycle;
        cycles.add(currentCycle);
        stateService.addCycle(currentCycle);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        // Once the genesis block is parsed we add the stateChangeEvents with the initial values from the
        // default param values to the state.
        stateService.registerStateChangeEventsProvider(txBlock ->
                provideStateChangeEvents(txBlock, stateService.getGenesisBlockHeight()));

        stateService.addBlockListener(new StateService.BlockListener() {
            @Override
            public boolean executeOnUserThread() {
                return false;
            }

            @Override
            public void onStartParsingBlock(int blockHeight) {
                chainHeight = blockHeight;

                // We want to set the correct phase and cycle before we start parsing a new block.
                // For Genesis block we did it already in the constructor
                // We copy over the phases from the current block as we get the phase only set in
                // applyParamToPhasesInCycle if there was a changeEvent.
                // The isFirstBlockInCycle methods returns from the previous cycle the first block as we have not
                // applied the new cycle yet. But the first block of the old cycle will always be the same as the
                // first block of the new cycle.
                if (blockHeight != stateService.getGenesisBlockHeight() && isFirstBlockAfterPreviousCycle(blockHeight)) {
                    Set<StateChangeEvent> stateChangeEvents = stateService.getLastBlock().getStateChangeEvents();
                    Cycle cycle = getNewCycle(blockHeight, currentCycle, stateChangeEvents);
                    currentCycle = cycle;
                    cycles.add(cycle);
                    stateService.addCycle(cycle);
                }
            }

            @Override
            public void onBlockAdded(Block block) {
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setup cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Cycle getNewCycle(int blockHeight, Cycle currentCycle, Set<StateChangeEvent> stateChangeEvents) {
        Cycle cycle = new Cycle(blockHeight, currentCycle.getPhaseItems());
        stateChangeEvents.stream()
                .filter(event -> event instanceof AddChangeParamEvent)
                .map(event -> (AddChangeParamEvent) event)
                .forEach(event -> applyParamToPhasesInCycle(event.getChangeParamPayload(), cycle));
        return cycle;
    }

    private boolean isFirstBlockAfterPreviousCycle(int height) {
        final Optional<Cycle> previousCycle = getCycle(height - 1);
        return previousCycle
                .filter(cycle -> cycle.getHeightOfLastBlock() + 1 == height)
                .isPresent();
    }

    private Set<StateChangeEvent> provideStateChangeEvents(TxBlock txBlock, int genesisBlockHeight) {
        final int height = txBlock.getHeight();
        if (height == genesisBlockHeight)
            return getStateChangeEventsFromParamDefaultValues(height);
        else
            return new HashSet<>();
    }

    private Set<StateChangeEvent> getStateChangeEventsFromParamDefaultValues(int height) {
        Set<StateChangeEvent> stateChangeEvents = new HashSet<>();
        Arrays.asList(Phase.values())
                .forEach(phase -> initWithDefaultValueAtGenesisHeight(phase, height)
                        .ifPresent(stateChangeEvents::add));
        return stateChangeEvents;
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


    private void applyParamToPhasesInCycle(ChangeParamPayload changeParamPayload, Cycle cycle) {
        final String paramName = changeParamPayload.getParam().name();
        final String phaseName = paramName.replace("PHASE_", "");
        final Phase phase = Phase.valueOf(phaseName);
        cycle.setPhaseObject(new PhaseItem(phase, (int) changeParamPayload.getValue()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Phase getCurrentPhase() {
        return currentCycle.getPhaseForHeight(chainHeight).get();
    }

    public boolean isFirstBlockInCycle(int height) {
        return getCycle(height)
                .filter(cycle -> cycle.getHeightOfFirstBlock() == height)
                .isPresent();
    }

    public boolean isLastBlockInCycle(int height) {
        return getCycle(height)
                .filter(cycle -> cycle.getHeightOfLastBlock() == height)
                .isPresent();
    }

    public Optional<Cycle> getCycle(int height) {
        return cycles.stream()
                .filter(cycle -> cycle.getHeightOfFirstBlock() <= height)
                .filter(cycle -> cycle.getHeightOfLastBlock() >= height)
                .findAny();
    }

    public boolean isInPhase(int height, Phase phase) {
        return getCycle(height)
                .filter(cycle -> cycle.isInPhase(height, phase))
                .isPresent();
    }

    public boolean isTxInPhase(String txId, Phase phase) {
        return stateService.getTx(txId)
                .filter(tx -> isInPhase(tx.getBlockHeight(), phase))
                .isPresent();
    }

    public Phase getPhaseForHeight(int height) {
        return getCycle(height)
                .flatMap(cycle -> cycle.getPhaseForHeight(height))
                .orElse(Phase.UNDEFINED);
    }

    public boolean isTxInCorrectCycle(int txHeight, int chainHeadHeight) {
        return getCycle(txHeight)
                .filter(cycle -> chainHeadHeight >= cycle.getHeightOfFirstBlock())
                .filter(cycle -> chainHeadHeight <= cycle.getHeightOfLastBlock())
                .isPresent();
    }

    public boolean isTxInCorrectCycle(String txId, int chainHeadHeight) {
        return stateService.getTx(txId)
                .filter(tx -> isTxInCorrectCycle(tx.getBlockHeight(), chainHeadHeight))
                .isPresent();
    }

    public boolean isTxInPastCycle(int txHeight, int chainHeadHeight) {
        return getCycle(txHeight)
                .filter(cycle -> chainHeadHeight > cycle.getHeightOfLastBlock())
                .isPresent();
    }

    public int getDurationForPhase(Phase phase, int height) {
        return getCycle(height)
                .map(cycle -> cycle.getDurationOfPhase(phase))
                .orElse(0);
    }

    public boolean isTxInPastCycle(String txId, int chainHeadHeight) {
        return stateService.getTx(txId)
                .filter(tx -> isTxInPastCycle(tx.getBlockHeight(), chainHeadHeight))
                .isPresent();
    }

    public int getFirstBlockOfPhase(int height, Phase phase) {
        return getCycle(height)
                .map(cycle -> cycle.getFirstBlockOfPhase(phase))
                .orElse(0);
    }

    public int getLastBlockOfPhase(int height, Phase phase) {
        return getCycle(height)
                .map(cycle -> cycle.getLastBlockOfPhase(phase))
                .orElse(0);
    }

}

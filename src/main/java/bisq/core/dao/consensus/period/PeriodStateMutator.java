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

package bisq.core.dao.consensus.period;

import bisq.core.dao.consensus.state.Block;
import bisq.core.dao.consensus.state.BlockListener;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.TxBlock;
import bisq.core.dao.consensus.state.events.AddChangeParamEvent;
import bisq.core.dao.consensus.state.events.StateChangeEvent;
import bisq.core.dao.consensus.vote.proposal.param.ChangeParamItem;
import bisq.core.dao.consensus.vote.proposal.param.Param;

import com.google.inject.Inject;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Writes data to the PeriodState.
 */
@Slf4j
public class PeriodStateMutator {
    private final PeriodState periodState;
    private final StateService stateService;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodStateMutator(PeriodState periodState, StateService stateService) {
        this.periodState = periodState;
        this.stateService = stateService;
    }

    void initialize() {
        // We create the initial state already in the constructor as we have no guaranteed order for calls of
        // onAllServicesInitialized and we want to avoid that some client expect the initial state and gets executed
        // before our onAllServicesInitialized is called.
        initFromGenesisBlock();

        // Once the genesis block is parsed we add the stateChangeEvents with the initial values from the
        // default param values to the state.
        stateService.registerStateChangeEventsProvider(txBlock ->
                provideStateChangeEvents(txBlock, stateService.getGenesisBlockHeight()));

        stateService.addBlockListener(new BlockListener() {
            @Override
            public boolean executeOnUserThread() {
                return false;
            }

            @Override
            public void onStartParsingBlock(int blockHeight) {
                periodState.setChainHeight(blockHeight);

                // We want to set the correct phase and cycle before we start parsing a new block.
                // For Genesis block we did it already in the constructor
                // We copy over the phases from the current block as we get the phase only set in
                // applyParamToPhasesInCycle if there was a changeEvent.
                // The isFirstBlockInCycle methods returns from the previous cycle the first block as we have not
                // applied the new cycle yet. But the first block of the old cycle will always be the same as the
                // first block of the new cycle.
                if (blockHeight != stateService.getGenesisBlockHeight() && isFirstBlockAfterPreviousCycle(blockHeight)) {
                    Set<StateChangeEvent> stateChangeEvents = stateService.getLastBlock().getStateChangeEvents();
                    Cycle cycle = getNewCycle(blockHeight, periodState.getCurrentCycle(), stateChangeEvents);
                    periodState.setCurrentCycle(cycle);
                    periodState.addCycle(cycle);
                    stateService.addCycle(cycle);
                }
            }

            @Override
            public void onBlockAdded(Block block) {
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeriodSetup cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initFromGenesisBlock() {
        // We want to have the initial data set up before the genesis tx gets parsed so we do it here in the constructor
        // as onAllServicesInitialized might get called after the parser has started.
        // We add the default values from the Param enum to our StateChangeEvent list.
        List<PhaseWrapper> phaseWrapperList = Arrays.stream(Phase.values())
                .map(phase -> initWithDefaultValueAtGenesisHeight(phase, stateService.getGenesisBlockHeight())
                        .map(event -> getPhaseWrapper(event.getChangeParamPayload()))
                        .get())
                .collect(Collectors.toList());
        Cycle currentCycle = new Cycle(stateService.getGenesisBlockHeight(), ImmutableList.copyOf(phaseWrapperList));

        stateService.addCycle(currentCycle);

        periodState.setCurrentCycle(currentCycle);
        periodState.addCycle(currentCycle);
        periodState.setChainHeight(stateService.getGenesisBlockHeight());
    }

    private Cycle getNewCycle(int blockHeight, Cycle currentCycle, Set<StateChangeEvent> stateChangeEvents) {
        List<PhaseWrapper> phaseWrapperListFromChangeEvents = stateChangeEvents.stream()
                .filter(event -> event instanceof AddChangeParamEvent)
                .map(event -> (AddChangeParamEvent) event)
                .map(event -> getPhaseWrapper(event.getChangeParamPayload()))
                .collect(Collectors.toList());

        List<PhaseWrapper> phaseWrapperList = new ArrayList<>();
        for (int i = 0; i < currentCycle.getPhaseWrapperList().size(); i++) {
            PhaseWrapper currentPhase = currentCycle.getPhaseWrapperList().get(i);
            // If we have a change event for that phase we use the new wrapper. Otherwise we use the same as in the
            // current cycle.
            if (isPhaseInList(phaseWrapperListFromChangeEvents, currentPhase.getPhase()))
                phaseWrapperList.add(phaseWrapperListFromChangeEvents.get(i));
            else
                phaseWrapperList.add(currentPhase);
        }
        return new Cycle(blockHeight, ImmutableList.copyOf(phaseWrapperList));
    }

    private boolean isPhaseInList(List<PhaseWrapper> list, Phase phase) {
        return list.stream().anyMatch(phaseWrapper -> phaseWrapper.getPhase() == phase);
    }

    private PhaseWrapper getPhaseWrapper(ChangeParamItem changeParamItem) {
        final String paramName = changeParamItem.getParam().name();
        final String phaseName = paramName.replace("PHASE_", "");
        final Phase phase = Phase.valueOf(phaseName);
        return new PhaseWrapper(phase, (int) changeParamItem.getValue());
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
                .map(param -> new ChangeParamItem(param, param.getDefaultValue()))
                .map(changeParamItem -> new AddChangeParamEvent(changeParamItem, height))
                .findAny();
    }

    private boolean isParamMatchingPhase(Param param, Phase phase) {
        return param.name().replace("PHASE_", "").equals(phase.name());
    }

    private Optional<Cycle> getCycle(int height) {
        return periodState.getCycles().stream()
                .filter(cycle -> cycle.getHeightOfFirstBlock() <= height)
                .filter(cycle -> cycle.getHeightOfLastBlock() >= height)
                .findAny();
    }
}

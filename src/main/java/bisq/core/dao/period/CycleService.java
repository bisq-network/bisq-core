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

package bisq.core.dao.period;

import bisq.core.dao.state.StartParsingListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.events.ParamChangeEvent;
import bisq.core.dao.state.events.StateChangeEvent;
import bisq.core.dao.voting.proposal.param.Param;
import bisq.core.dao.voting.proposal.param.ParamChange;

import com.google.inject.Inject;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CycleService implements StartParsingListener {
    private final StateService stateService;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CycleService(StateService stateService) {
        this.stateService = stateService;
        stateService.addStartParsingListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        // We create the initial state already in the constructor as we have no guaranteed order for calls of
        // onAllServicesInitialized and we want to avoid that some client expect the initial state and gets executed
        // before our onAllServicesInitialized is called.
        initFromGenesisBlock();
    }

    @Override
    public void onStartParsing(int blockHeight) {
        // We want to set the correct phase and cycle before we start parsing a new block.
        // For Genesis block we did it already in the start method.
        // We copy over the phases from the current block as we get the phase only set in
        // applyParamToPhasesInCycle if there was a changeEvent.
        // The isFirstBlockInCycle methods returns from the previous cycle the first block as we have not
        // applied the new cycle yet. But the first block of the old cycle will always be the same as the
        // first block of the new cycle.
        if (blockHeight != stateService.getGenesisBlockHeight() && isFirstBlockAfterPreviousCycle(blockHeight)) {
            // We take stateChangeEvents from last block of previous cycle
            Set<StateChangeEvent> stateChangeEvents = stateService.getLastBlock().getStateChangeEvents();

            // We have the not update stateService.getCurrentCycle() so we grab here the previousCycle
            final Cycle previousCycle = stateService.getCurrentCycle();
            // We create the new cycle as clone of the previous cycle and only if there have been change events we use
            // the new values from the change event.
            Cycle cycle = createNewCycle(blockHeight, previousCycle, stateChangeEvents);

            // We add the cycle to the state
            stateService.addCycle(cycle);
        }

        stateService.setChainHeight(blockHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeriodSetup cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initFromGenesisBlock() {
        // We want to have the initial data set up before the genesis tx gets parsed so we do it here in the constructor
        // as onAllServicesInitialized might get called after the parser has started.
        // We add the default values from the Param enum to our StateChangeEvent list.
        final int genesisBlockHeight = stateService.getGenesisBlockHeight();
        List<PhaseWrapper> phaseWrapperList = Arrays.stream(Phase.values())
                .map(phase -> initWithDefaultValueAtGenesisHeight(phase, genesisBlockHeight)
                        .map(event -> getPhaseWrapper(event.getParamChange()))
                        .get())
                .collect(Collectors.toList());
        Cycle currentCycle = new Cycle(genesisBlockHeight, ImmutableList.copyOf(phaseWrapperList));
        stateService.addCycle(currentCycle);
        stateService.setChainHeight(genesisBlockHeight);
    }

    private Cycle createNewCycle(int blockHeight, Cycle previousCycle, Set<StateChangeEvent> stateChangeEvents) {
        List<PhaseWrapper> phaseWrapperListFromChangeEvents = stateChangeEvents.stream()
                .filter(event -> event instanceof ParamChangeEvent)
                .map(event -> (ParamChangeEvent) event)
                .map(event -> getPhaseWrapper(event.getParamChange()))
                .collect(Collectors.toList());

        List<PhaseWrapper> phaseWrapperList = new ArrayList<>();
        for (int i = 0; i < previousCycle.getPhaseWrapperList().size(); i++) {
            PhaseWrapper phaseWrapper = previousCycle.getPhaseWrapperList().get(i);
            // If we have a change event for that phase we use the new wrapper. Otherwise we use the same as in the
            // previous cycle.
            if (isPhaseInList(phaseWrapperListFromChangeEvents, phaseWrapper.getPhase()))
                phaseWrapperList.add(phaseWrapperListFromChangeEvents.get(i));
            else
                phaseWrapperList.add(phaseWrapper);
        }
        return new Cycle(blockHeight, ImmutableList.copyOf(phaseWrapperList));
    }

    private boolean isPhaseInList(List<PhaseWrapper> list, Phase phase) {
        return list.stream().anyMatch(phaseWrapper -> phaseWrapper.getPhase() == phase);
    }

    private PhaseWrapper getPhaseWrapper(ParamChange paramChange) {
        final String paramName = paramChange.getParam().name();
        final String phaseName = paramName.replace("PHASE_", "");
        final Phase phase = Phase.valueOf(phaseName);
        return new PhaseWrapper(phase, (int) paramChange.getValue());
    }

    private boolean isFirstBlockAfterPreviousCycle(int height) {
        final Optional<Cycle> previousCycle = getCycle(height - 1);
        return previousCycle
                .filter(cycle -> cycle.getHeightOfLastBlock() + 1 == height)
                .isPresent();
    }

    private Optional<ParamChangeEvent> initWithDefaultValueAtGenesisHeight(Phase phase, int height) {
        return Arrays.stream(Param.values())
                .filter(param -> isParamMatchingPhase(param, phase))
                .map(param -> new ParamChange(param, param.getDefaultValue()))
                .map(paramChange -> new ParamChangeEvent(paramChange, height))
                .findAny();
    }

    private boolean isParamMatchingPhase(Param param, Phase phase) {
        return param.name().replace("PHASE_", "").equals(phase.name());
    }

    private Optional<Cycle> getCycle(int height) {
        return stateService.getCycles().stream()
                .filter(cycle -> cycle.getHeightOfFirstBlock() <= height)
                .filter(cycle -> cycle.getHeightOfLastBlock() >= height)
                .findAny();
    }




   /* private Set<StateChangeEvent> provideStateChangeEvents(TxBlock txBlock, int genesisBlockHeight) {
        final int height = txBlock.getHeight();
        if (height == genesisBlockHeight)
            return getStateChangeEventsFromParamDefaultValues(height);
        else
            return new HashSet<>();
    }*/

   /* private Set<StateChangeEvent> getStateChangeEventsFromParamDefaultValues(int height) {
        Set<StateChangeEvent> stateChangeEvents = new HashSet<>();
        Arrays.asList(Phase.values())
                .forEach(phase -> initWithDefaultValueAtGenesisHeight(phase, height)
                        .ifPresent(stateChangeEvents::add));
        return stateChangeEvents;
    }*/
}

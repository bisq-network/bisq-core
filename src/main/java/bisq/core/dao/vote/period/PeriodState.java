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

package bisq.core.dao.vote.period;

import bisq.core.dao.state.Block;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.TxBlock;
import bisq.core.dao.state.events.AddChangeParamEvent;
import bisq.core.dao.state.events.StateChangeEvent;
import bisq.core.dao.vote.proposal.param.ChangeParamPayload;
import bisq.core.dao.vote.proposal.param.Param;

import bisq.common.ThreadContextAwareListener;
import bisq.common.UserThread;

import com.google.inject.Inject;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

/**
 * Provide state about the phase and cycle of the monthly proposals and voting cycle.
 * A cycle is the sequence of distinct phases. The first cycle and phase starts with the genesis block height.
 *
 * This class should be accessed by the PeriodService only as it is designed to run in the parser thread.
 * Only exception is the listener which gets set from the UserThreadPeriodService/s user thread and is executed
 * by mapping and the immutable data to user thread. The cycles list gets cloned as that list is not
 * immutable (though Cycle is).
 */
@Slf4j
public class PeriodState {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener extends ThreadContextAwareListener {
        void onNewCycle(ImmutableList<Cycle> cycles, Cycle currentCycle);

        void onChainHeightChanged(int chainHeight);
    }

    private final StateService stateService;

    // We need to have a threadsafe list here as we might get added a listener from user thread during iteration
    // at parser thread.
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    // Mutable state
    private final List<Cycle> cycles = new ArrayList<>();
    private Cycle currentCycle;
    private int chainHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodState(StateService stateService) {
        this.stateService = stateService;

        // We create the initial state already in the constructor as we have no guaranteed order for calls of
        // onAllServicesInitialized and we want to avoid that some client expect the initial state and gets executed
        // before our onAllServicesInitialized is called.
        initFromGenesisBlock();

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Can be called from user thread.
    public void addListenerAndGetNotified(Listener listener) {
        listeners.add(listener);
        notifyListener(listener);
    }

    private void notifyListener(Listener listener) {
        final Cycle finalCurrentCycle = currentCycle;
        final int finalChainHeight = chainHeight;
        if (listener.executeOnUserThread()) {
            UserThread.execute(() -> {
                listener.onNewCycle(ImmutableList.copyOf(cycles), finalCurrentCycle);
                listener.onChainHeightChanged(finalChainHeight);
            });
        } else {
            listener.onNewCycle(ImmutableList.copyOf(cycles), finalCurrentCycle);
            listener.onChainHeightChanged(finalChainHeight);
        }
    }

    private void notifyListeners() {
        listeners.forEach(listener -> notifyListener(listener));
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

                    notifyListeners();
                }
                notifyListeners();
            }

            @Override
            public void onBlockAdded(Block block) {
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope
    ///////////////////////////////////////////////////////////////////////////////////////////

    List<Cycle> getCycles() {
        return cycles;
    }

    Cycle getCurrentCycle() {
        return currentCycle;
    }

    int getChainHeight() {
        return chainHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setup cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initFromGenesisBlock() {
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

        notifyListeners();
    }

    private Cycle getNewCycle(int blockHeight, Cycle currentCycle, Set<StateChangeEvent> stateChangeEvents) {
        Cycle cycle = new Cycle(blockHeight, currentCycle.getPhaseWrapperList());
        stateChangeEvents.stream()
                .filter(event -> event instanceof AddChangeParamEvent)
                .map(event -> (AddChangeParamEvent) event)
                .forEach(event -> applyParamToPhasesInCycle(event.getChangeParamPayload(), cycle));
        return cycle;
    }

    private void applyParamToPhasesInCycle(ChangeParamPayload changeParamPayload, Cycle cycle) {
        final String paramName = changeParamPayload.getParam().name();
        final String phaseName = paramName.replace("PHASE_", "");
        final Phase phase = Phase.valueOf(phaseName);
        cycle.setPhaseWrapper(new PhaseWrapper(phase, (int) changeParamPayload.getValue()));
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

    private Optional<Cycle> getCycle(int height) {
        return cycles.stream()
                .filter(cycle -> cycle.getHeightOfFirstBlock() <= height)
                .filter(cycle -> cycle.getHeightOfLastBlock() >= height)
                .findAny();
    }
}

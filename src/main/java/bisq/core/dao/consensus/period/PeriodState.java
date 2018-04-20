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

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

/**
 * Provide state about the phase and cycle of the monthly proposals and voting cycle.
 * A cycle is the sequence of distinct phases. The first cycle and phase starts with the genesis block height.
 *
 * This class should be accessed by the PeriodService only as it is designed to run in the parser thread.
 * Only exception is the listener which gets set from the PeriodServiceFacade/s user thread and is executed
 * by mapping and the immutable data to user thread. The cycles list gets cloned as that list is not
 * immutable (though Cycle is).
 */
@Slf4j
public class PeriodState {
    // We need to have a threadsafe list here as we might get added a listener from user thread during iteration
    // at parser thread.
    private final List<PeriodStateChangeListener> periodStateChangeListeners = new CopyOnWriteArrayList<>();

    // Mutable state
    private final List<Cycle> cycles = new ArrayList<>();
    private Cycle currentCycle;
    private int chainHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodState() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Can be called from user thread.
    public void addListenerAndGetNotified(PeriodStateChangeListener periodStateChangeListener) {
        periodStateChangeListeners.add(periodStateChangeListener);

        periodStateChangeListeners.forEach(l -> l.execute(() -> l.onInitialState(cycles, currentCycle, chainHeight)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setChainHeight(int chainHeight) {
        this.chainHeight = chainHeight;
        periodStateChangeListeners.forEach(listener -> listener.execute(() -> listener.onPreParserChainHeightChanged(chainHeight)));
    }

    public void setCurrentCycle(Cycle currentCycle) {
        this.currentCycle = currentCycle;
        periodStateChangeListeners.forEach(listener -> listener.execute(() -> listener.onCurrentCycleChanged(currentCycle)));
    }

    public void addCycle(Cycle cycle) {
        this.cycles.add(cycle);
        periodStateChangeListeners.forEach(listener -> listener.execute(() -> listener.onCycleAdded(cycle)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<Cycle> getCycles() {
        return cycles;
    }

    public Cycle getCurrentCycle() {
        return currentCycle;
    }

    public int getChainHeight() {
        return chainHeight;
    }

    public void setCycles(List<Cycle> cycles) {
        this.cycles.clear();
        this.cycles.addAll(cycles);
    }
}

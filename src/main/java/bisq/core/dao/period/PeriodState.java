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

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Provide state about the phase and cycle of the monthly proposals and voting cycle.
 * A cycle is the sequence of distinct phases. The first cycle and phase starts with the genesis block height.
 */
@Slf4j
public class PeriodState {
    private final List<PeriodStateChangeListener> periodStateChangeListeners = new ArrayList<>();

    // Mutable state
    private final List<Cycle> cycles;
    private Cycle currentCycle;
    private int chainHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodState() {
        this(new ArrayList<>(), null, 0);
    }

    private PeriodState(List<Cycle> cycles, Cycle currentCycle, int chainHeight) {
        this.cycles = cycles;
        this.currentCycle = currentCycle;
        this.chainHeight = chainHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    void addPeriodStateChangeListener(PeriodStateChangeListener periodStateChangeListener) {
        periodStateChangeListeners.add(periodStateChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setChainHeight(int chainHeight) {
        this.chainHeight = chainHeight;
        periodStateChangeListeners.forEach(listener -> listener.onChainHeightChanged(chainHeight));
    }

    void setCurrentCycle(Cycle currentCycle) {
        this.currentCycle = currentCycle;
        periodStateChangeListeners.forEach(listener -> listener.onCurrentCycleChanged(currentCycle));
    }

    void addCycle(Cycle cycle) {
        this.cycles.add(cycle);
        periodStateChangeListeners.forEach(listener -> listener.onCycleAdded(cycle));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
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

    void setCycles(List<Cycle> cycles) {
        this.cycles.clear();
        this.cycles.addAll(cycles);
    }
}

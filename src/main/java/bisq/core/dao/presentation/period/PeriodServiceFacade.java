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

package bisq.core.dao.presentation.period;

import bisq.core.dao.consensus.period.BasePeriodService;
import bisq.core.dao.consensus.period.Cycle;
import bisq.core.dao.consensus.period.PeriodState;
import bisq.core.dao.consensus.period.PeriodStateListener;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.presentation.state.StateServiceFacade;

import com.google.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Subclass of BasePeriodService which is designed to run in the user thread.
 *
 * State from PeriodState and StateService gets mapped from the parser thread to the user thread in the StateService.
 * At the handlers we get the state fields either as immutable data or as cloned arrayList.
 *
 * The listeners are called delayed from the parser threads perspective so they must not be used for consensus
 * critical code but should be used for presentation only where exact timing is not crucial.
 */
@Slf4j
public final class PeriodServiceFacade extends BasePeriodService implements PeriodStateListener {
    private final StateServiceFacade stateServiceFacade;

    // We maintain a local version of the period state which gets updated when the main PeriodState gets changed.
    // We get the update mapped to the user thread as the PeriodState is written via the parser thread.
    // By keeping the model separated from another thread context the client classed do not need to worry about
    // threading issues.
    private final PeriodState userThreadPeriodState;

    private final ObjectProperty<Phase> phaseProperty = new SimpleObjectProperty<>(Phase.UNDEFINED);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodServiceFacade(PeriodState periodState, StateServiceFacade stateServiceFacade) {
        this.stateServiceFacade = stateServiceFacade;

        userThreadPeriodState = new PeriodState();

        periodState.addListenerAndGetNotified(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeriodStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get called on the user thread
    @Override
    public void onChainHeightChanged(int chainHeight) {
        userThreadPeriodState.setChainHeight(chainHeight);
        updatePhaseProperty();
    }

    @Override
    public void onCurrentCycleChanged(Cycle currentCycle) {
        userThreadPeriodState.setCurrentCycle(currentCycle);
        updatePhaseProperty();
    }

    @Override
    public void onCycleAdded(Cycle cycle) {
        userThreadPeriodState.addCycle(cycle);
        updatePhaseProperty();
    }

    @Override
    public void onGetInitialState(List<Cycle> cycles, Cycle currentCycle, int chainHeight) {
        userThreadPeriodState.setChainHeight(chainHeight);
        userThreadPeriodState.setCurrentCycle(currentCycle);
        userThreadPeriodState.setCycles(cycles);
        updatePhaseProperty();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement BasePeriodService methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public List<Cycle> getCycles() {
        return userThreadPeriodState.getCycles();
    }

    @Override
    public Cycle getCurrentCycle() {
        return userThreadPeriodState.getCurrentCycle();
    }

    @Override
    public int getChainHeight() {
        return userThreadPeriodState.getChainHeight();
    }

    //TODO
    @Override
    public Optional<Tx> getOptionalTx(String txId) {
        return stateServiceFacade.getTx(txId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Phase getPhase() {
        return phaseProperty.get();
    }

    public ObjectProperty<Phase> phaseProperty() {
        return phaseProperty;
    }


    private void updatePhaseProperty() {
        if (getChainHeight() > 0 && getCurrentCycle() != null)
            getCurrentCycle().getPhaseForHeight(getChainHeight()).ifPresent(phaseProperty::set);
    }
}

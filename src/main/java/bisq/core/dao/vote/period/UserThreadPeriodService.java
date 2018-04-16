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

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;

import com.google.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Version of BasePeriodService which is expected to run in the user thread.
 *
 * State from PeriodState and StateService gets mapped from the parser thread to the user thread in the StateService.
 * At the handlers we get the state fields either as immutable data or as cloned arrayList.
 *
 * The listeners are called delayed from the parser threads perspective so they must not be used for consensus
 * critical code but used for presentation only where exact timing is not crucial.
 *
 * We protect access requests with a check if the caller is really in the user thread.
 */
@Slf4j
public final class UserThreadPeriodService extends BasePeriodService implements PeriodState.Listener {
    //TODO remove
    private final StateService stateService;

    private List<Cycle> cycles = new ArrayList<>();
    private Cycle currentCycle;
    private int chainHeight;

    private final ObjectProperty<Phase> phaseProperty = new SimpleObjectProperty<>(Phase.UNDEFINED);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public UserThreadPeriodService(PeriodState periodState, StateService stateService) {
        this.stateService = stateService;

        periodState.addListenerAndGetNotified(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeriodState.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get called on the user thread
    @Override
    public void onNewCycle(List<Cycle> cycles, Cycle currentCycle) {
        this.cycles = cycles;
        this.currentCycle = currentCycle;
        updatePhaseProperty();
    }

    // We get called on the user thread
    @Override
    public void onChainHeightChanged(int chainHeight) {
        this.chainHeight = chainHeight;
        updatePhaseProperty();
    }

    private void updatePhaseProperty() {
        if (chainHeight > 0 && currentCycle != null)
            currentCycle.getPhaseForHeight(chainHeight).ifPresent(phaseProperty::set);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BasePeriodService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected List<Cycle> provideCycles() {

        return cycles;
    }

    @Override
    protected Cycle provideCurrentCycle() {
        return currentCycle;
    }

    //TODO
    @Override
    protected Optional<Tx> provideTx(String txId) {
        return stateService.getTx(txId);
    }

    @Override
    protected int provideHeight() {
        return chainHeight;
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
}

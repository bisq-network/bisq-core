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
import bisq.core.dao.consensus.state.BlockListener;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;

import com.google.inject.Inject;

import com.google.common.collect.ImmutableList;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
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
    //TODO remove
    private final StateService stateService;

    private ImmutableList<Cycle> cycles;
    private Cycle currentCycle;
    private int chainHeight;

    private final ObjectProperty<Phase> phaseProperty = new SimpleObjectProperty<>(Phase.UNDEFINED);
    private List<BlockListener> blockListeners = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodServiceFacade(PeriodState periodState, StateService stateService) {
        this.stateService = stateService;

        periodState.addListenerAndGetNotified(this);
        stateService.addBlockListener(block -> blockListeners.forEach(l -> l.execute(() -> l.onBlockAdded(block))));
    }

    public void addBlockListener(BlockListener blockListener) {
        blockListeners.add(blockListener);
    }

    public void removeBlockListener(BlockListener blockListener) {
        blockListeners.remove(blockListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeriodStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get called on the user thread
    @Override
    public void onNewCycle(ImmutableList<Cycle> cycles, Cycle currentCycle) {
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
    // Implement BasePeriodService methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public List<Cycle> getCycles() {
        return cycles;
    }

    @Override
    public Cycle getCurrentCycle() {
        return currentCycle;
    }

    //TODO
    @Override
    public Optional<Tx> getOptionalTx(String txId) {
        return stateService.getTx(txId);
    }

    @Override
    public int getChainHeight() {
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

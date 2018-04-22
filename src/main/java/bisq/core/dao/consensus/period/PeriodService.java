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

import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;

import com.google.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PeriodService {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addPeriodStateChangeListener(PeriodStateChangeListener listener) {
        periodStateChangeListeners.add(listener);
    }

    public void removePeriodStateChangeListener(PeriodStateChangeListener listener) {
        periodStateChangeListeners.remove(listener);
    }


    private final StateService stateService;
    private final PeriodState periodState;

    private final ObjectProperty<Phase> phaseProperty = new SimpleObjectProperty<>(Phase.UNDEFINED);
    private final List<PeriodStateChangeListener> periodStateChangeListeners = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodService(StateService stateService, PeriodState periodState) {
        this.stateService = stateService;
        this.periodState = periodState;

        periodState.addPeriodStateChangeListener(new PeriodStateChangeListener() {
            @Override
            public void onPreParserChainHeightChanged(int chainHeight) {
                periodStateChangeListeners.forEach(l -> l.onPreParserChainHeightChanged(chainHeight));
                updatePhaseProperty();
            }

            @Override
            public void onCurrentCycleChanged(Cycle currentCycle) {
                periodStateChangeListeners.forEach(l -> l.onCurrentCycleChanged(currentCycle));
                updatePhaseProperty();
            }

            @Override
            public void onCycleAdded(Cycle cycle) {
                periodStateChangeListeners.forEach(l -> l.onCycleAdded(cycle));
                updatePhaseProperty();
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<Cycle> getCycles() {
        return periodState.getCycles();
    }

    public Cycle getCurrentCycle() {
        return periodState.getCurrentCycle();
    }

    public int getChainHeight() {
        return periodState.getChainHeight();
    }

    public Optional<Tx> getOptionalTx(String txId) {
        return stateService.getTx(txId);
    }

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

    public Phase getCurrentPhase() {
        return getCurrentCycle().getPhaseForHeight(this.getChainHeight()).get();
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
        return getCycles().stream()
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
        return getOptionalTx(txId)
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
        return getOptionalTx(txId)
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
        return getOptionalTx(txId)
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

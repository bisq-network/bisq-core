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

import bisq.core.dao.state.StateService;

import com.google.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Thead safe version of PeriodService. UI code should use only that class.
 */
@Slf4j
public class ThreadSafePeriodService {

    private final PeriodService periodService;
    private final StateService stateService;
    @Getter
    private final ObjectProperty<Phase> phaseProperty = new SimpleObjectProperty<>(Phase.UNDEFINED);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ThreadSafePeriodService(PeriodService periodService, StateService stateService) {
        this.periodService = periodService;
        this.stateService = stateService;
    }

    public void onAllServicesInitialized() {
        stateService.addBlockListener(block ->
                periodService.getCurrentCycle().getPhaseForHeight(block.getHeight())
                        .ifPresent(phaseProperty::set));
    }

    public synchronized int getDurationForPhase(Phase phase, int height) {
        return periodService.getDurationForPhase(phase, height);
    }

    public synchronized boolean isTxInPastCycle(String txId, int chainHeadHeight) {
        return periodService.isTxInPastCycle(txId, chainHeadHeight);
    }

    public synchronized int getFirstBlockOfPhase(int height, Phase phase) {
        return periodService.getFirstBlockOfPhase(height, phase);
    }

    public synchronized int getLastBlockOfPhase(int height, Phase phase) {
        return periodService.getLastBlockOfPhase(height, phase);
    }

    public synchronized int getChainHeight() {
        return periodService.getChainHeight();
    }
}

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

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Subclass of BasePeriodService which is designed to run in the parser thread.
 * It provides the mutable data from the PeriodState and StateService. Both those data sources are as well
 * designed to run in the parser thread so we don't provide synchronisation support as we are inside a
 * single threaded model.
 */
@Slf4j
public final class PeriodService extends BasePeriodService {
    private final StateService stateService;
    private final PeriodState periodState;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PeriodService(PeriodStateMutator periodStateMutator, StateService stateService, PeriodState periodState) {
        this.stateService = stateService;
        this.periodState = periodState;

        periodStateMutator.initialize();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement BasePeriodService methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public List<Cycle> getCycles() {
        return periodState.getCycles();
    }

    @Override
    public Cycle getCurrentCycle() {
        return periodState.getCurrentCycle();
    }

    @Override
    public int getChainHeight() {
        return periodState.getChainHeight();
    }

    @Override
    public Optional<Tx> getOptionalTx(String txId) {
        return stateService.getTx(txId);
    }

}

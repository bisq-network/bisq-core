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
    public PeriodService(StateService stateService, PeriodState periodState) {
        this.stateService = stateService;
        this.periodState = periodState;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BasePeriodService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected List<Cycle> provideCycles() {
        return periodState.getCycles();
    }

    @Override
    protected Cycle provideCurrentCycle() {
        return periodState.getCurrentCycle();
    }

    @Override
    protected Optional<Tx> provideTx(String txId) {
        return stateService.getTx(txId);
    }

    @Override
    protected int provideHeight() {
        return periodState.getChainHeight();
    }
}

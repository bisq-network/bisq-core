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

import bisq.core.dao.param.DaoParam;
import bisq.core.dao.param.DaoParamService;

import org.powermock.core.classloader.annotations.PrepareForTest;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@PrepareForTest({DaoParamService.class})
public class CyclesTest {
    private Cycles cycles;
    private int genesisHeight = 200;
    DaoParamService daoParamService = mock(DaoParamService.class);
    // All phases are 10 blocks long during test
    long phaseDuration = 10L;

    @Before
    public void startup() {
        when(daoParamService.getDaoParamValue(any(DaoParam.class), anyInt())).thenReturn(phaseDuration);
        when(daoParamService.getDaoParamValue(eq(DaoParam.PHASE_UNDEFINED), anyInt())).thenReturn(0L);
        when(daoParamService.getDaoParamValue(eq(DaoParam.PHASE_ISSUANCE), anyInt())).thenReturn(1L);

        cycles = new Cycles(genesisHeight, daoParamService);
    }

    @Test
    public void getCycle() {
        int newCycleStartBlock = cycles.getCycle(genesisHeight).getLastBlock() + 1;
        cycles.onChainHeightChanged(newCycleStartBlock);
        assertEquals(cycles.getCycle(genesisHeight), cycles.getCycle(newCycleStartBlock - 1));
        assertEquals(cycles.getCycle(newCycleStartBlock), cycles.getCycle(newCycleStartBlock + 1));
        assertEquals(cycles.getCycle(0), cycles.getCycle(genesisHeight - 1));
        assertNotEquals(cycles.getCycle(0), cycles.getCycle(genesisHeight));
        assertEquals(cycles.getCycle(genesisHeight).getPhaseDuration(Cycles.Phase.PROPOSAL), phaseDuration);
    }

    @Test
    public void getPhase() {
        int newCycleStartBlock = cycles.getCycle(genesisHeight).getLastBlock() + 1;
        cycles.onChainHeightChanged(newCycleStartBlock);
        assertEquals(Cycles.Phase.PROPOSAL, cycles.getPhase(genesisHeight));
        assertEquals(Cycles.Phase.PROPOSAL, cycles.getPhase(genesisHeight + (int) phaseDuration - 1));
        assertEquals(Cycles.Phase.BREAK1, cycles.getPhase(genesisHeight + (int) phaseDuration));
        assertEquals(Cycles.Phase.BREAK4,
                cycles.getPhase(genesisHeight + cycles.getCycle(genesisHeight).getCycleDuration() - 1));
        assertEquals(Cycles.Phase.PROPOSAL,
                cycles.getPhase(genesisHeight + cycles.getCycle(genesisHeight).getCycleDuration()));
    }

    @Test
    public void getStartBlockOfPhase() {
        Cycles.Cycle c = cycles.getCycle(genesisHeight);
        assertEquals(genesisHeight, cycles.getStartBlockOfPhase(genesisHeight, Cycles.Phase.PROPOSAL));
        assertEquals(genesisHeight + phaseDuration,
                cycles.getStartBlockOfPhase(genesisHeight, Cycles.Phase.BREAK1));
    }

    @Test
    public void getEndBlockOfPhase() {
        int newCycleStartBlock = cycles.getCycle(genesisHeight).getLastBlock() + 1;
        cycles.onChainHeightChanged(newCycleStartBlock);
        Cycles.Cycle c = cycles.getCycle(genesisHeight);
        assertEquals(genesisHeight + phaseDuration - 1,
                cycles.getLastBlockOfPhase(genesisHeight, Cycles.Phase.PROPOSAL));
        assertEquals(genesisHeight + 2 * phaseDuration - 1,
                cycles.getLastBlockOfPhase(genesisHeight, Cycles.Phase.BREAK1));
    }

    @Test
    public void getNumberOfStartedCycles() {
    }

    @Test
    public void onChainHeightChanged() {
        int newCycleStartBlock = cycles.getCycle(genesisHeight).getLastBlock() + 1;
        cycles.onChainHeightChanged(newCycleStartBlock);
        assertNotEquals(cycles.getCycle(genesisHeight), cycles.getCycle(newCycleStartBlock));
    }
}

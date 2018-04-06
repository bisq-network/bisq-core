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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;


public class CyclesTest {
    private Cycles cycles;
    private int genesisHeight = 200;

    @Before
    public void startup() {
        cycles = new Cycles(genesisHeight);
    }

    @Test
    public void getCycle() {
        int newCycleStartBlock = cycles.getCycle(genesisHeight).getEndBlock() + 1;
        cycles.onChainHeightChanged(newCycleStartBlock);
        assertEquals(cycles.getCycle(genesisHeight), cycles.getCycle(newCycleStartBlock - 1));
        assertEquals(cycles.getCycle(newCycleStartBlock), cycles.getCycle(newCycleStartBlock + 1));
        assertEquals(cycles.getCycle(0), cycles.getCycle(genesisHeight - 1));
        assertNotEquals(cycles.getCycle(0), cycles.getCycle(genesisHeight));
    }

    @Test
    public void getPhase() {
    }

    @Test
    public void getStartBlockOfPhase() {
    }

    @Test
    public void getEndBlockOfPhase() {
    }

    @Test
    public void getNumberOfStartedCycles() {
    }

    @Test
    public void onChainHeightChanged() {
        int newCycleStartBlock = cycles.getCycle(genesisHeight).getEndBlock() + 1;
        cycles.onChainHeightChanged(newCycleStartBlock);
        assertNotEquals(cycles.getCycle(genesisHeight), cycles.getCycle(newCycleStartBlock));
    }
}

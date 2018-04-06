/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.vote;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PeriodServiceTest {

    private PeriodService service;

    @Before
    public void startup() {
//        service = new PeriodService(null, 0);
    }

    //TODO update with added periods
    @Test
    public void calculatePhaseTest() {
        /*      UNDEFINED(0),
              phase1  COMPENSATION_REQUESTS(144 * 23), // 3312
              phase2  BREAK1(10), 3322
              phase3  BLIND_VOTE(144 * 4), // 3322 + 576 = 3898
              phase4  BREAK2(10), 3908
              phase5  VOTE_REVEAL(144 * 3), // 3908 + 432 = 4340
              phase6  BREAK3(10); 4350
        */
//        int totalPhaseBlocks = service.getNumBlocksOfCycle();

//        int phase1 = Cycles.Phase.PROPOSAL.getCycleDuration();
//        int phase2 = phase1 + Cycles.Phase.BREAK1.getCycleDuration();
//        int phase3 = phase2 + Cycles.Phase.BLIND_VOTE.getCycleDuration();
//        int phase4 = phase3 + Cycles.Phase.BREAK2.getCycleDuration();
//        int phase5 = phase4 + Cycles.Phase.VOTE_REVEAL.getCycleDuration();
//        int phase6 = phase5 + Cycles.Phase.BREAK3.getCycleDuration();

//        assertEquals(Cycles.Phase.PROPOSAL, service.calculatePhase(service.getRelativeBlocksInCycle(0, 0, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.PROPOSAL, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase1 - 1, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.BREAK1, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase1, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.BREAK1, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase2 - 1, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.BLIND_VOTE, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase2, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.BLIND_VOTE, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase3 - 1, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.BREAK2, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase3, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.BREAK2, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase4 - 1, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.VOTE_REVEAL, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase4, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.VOTE_REVEAL, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase5 - 1, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.BREAK3, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase5, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.BREAK3, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase6 - 1, totalPhaseBlocks)));
//        assertEquals(Cycles.Phase.ISSUANCE, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase6, totalPhaseBlocks)));
    }

    @Test
    public void getNumOfStartedCyclesTest() {
        // int chainHeight, int genesisHeight, int numBlocksOfCycle
//        int numBlocksOfCycle = service.getNumBlocksOfCycle();
//        int genesisHeight = 1;
//        assertEquals(0, service.getNumOfStartedCycles(genesisHeight - 1, genesisHeight, numBlocksOfCycle));
//        assertEquals(1, service.getNumOfStartedCycles(genesisHeight, genesisHeight, numBlocksOfCycle));
//        assertEquals(1, service.getNumOfStartedCycles(genesisHeight + 1, genesisHeight, numBlocksOfCycle));
//        assertEquals(1, service.getNumOfStartedCycles(genesisHeight + numBlocksOfCycle - 1, genesisHeight, numBlocksOfCycle));
//        assertEquals(2, service.getNumOfStartedCycles(genesisHeight + numBlocksOfCycle, genesisHeight, numBlocksOfCycle));
//        assertEquals(2, service.getNumOfStartedCycles(genesisHeight + numBlocksOfCycle + 1, genesisHeight, numBlocksOfCycle));
//        assertEquals(2, service.getNumOfStartedCycles(genesisHeight + numBlocksOfCycle + numBlocksOfCycle - 1, genesisHeight, numBlocksOfCycle));
//        assertEquals(3, service.getNumOfStartedCycles(genesisHeight + numBlocksOfCycle + numBlocksOfCycle, genesisHeight, numBlocksOfCycle));
    }

    @Test
    public void getNumOfCompletedCyclesTest() {
        // int chainHeight, int genesisHeight, int totalPeriodInBlocks
//        int numBlocksOfCycle = service.getNumBlocksOfCycle();
//        int genesisHeight = 1;
//        assertEquals(0, service.getNumOfCompletedCycles(genesisHeight - 1, genesisHeight, numBlocksOfCycle));
//        assertEquals(0, service.getNumOfCompletedCycles(genesisHeight, genesisHeight, numBlocksOfCycle));
//        assertEquals(0, service.getNumOfCompletedCycles(genesisHeight + 1, genesisHeight, numBlocksOfCycle));
//        assertEquals(0, service.getNumOfCompletedCycles(genesisHeight + numBlocksOfCycle - 1, genesisHeight, numBlocksOfCycle));
//        assertEquals(1, service.getNumOfCompletedCycles(genesisHeight + numBlocksOfCycle, genesisHeight, numBlocksOfCycle));
//        assertEquals(1, service.getNumOfCompletedCycles(genesisHeight + numBlocksOfCycle + 1, genesisHeight, numBlocksOfCycle));
//        assertEquals(1, service.getNumOfCompletedCycles(genesisHeight + numBlocksOfCycle + numBlocksOfCycle - 1, genesisHeight, numBlocksOfCycle));
//        assertEquals(2, service.getNumOfCompletedCycles(genesisHeight + numBlocksOfCycle + numBlocksOfCycle, genesisHeight, numBlocksOfCycle));
    }

    //TODO update with added periods
    @Test
    public void getCompensationRequestStartBlockTest() {
        // int chainHeight, int genesisHeight, int totalPeriodInBlocks
//        int numBlocksOfCycle = service.getNumBlocksOfCycle();
//        int gen = 1;
//        final int first = gen; // 1
//        final int second = first + numBlocksOfCycle; //
//        final int third = first + numBlocksOfCycle + numBlocksOfCycle; //
//        assertEquals(gen, service.getAbsoluteStartBlockOfPhase(0, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(gen, service.getAbsoluteStartBlockOfPhase(gen, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(first, service.getAbsoluteStartBlockOfPhase(gen + 1, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(first, service.getAbsoluteStartBlockOfPhase(gen + numBlocksOfCycle - 1, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(second, service.getAbsoluteStartBlockOfPhase(gen + numBlocksOfCycle, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(second, service.getAbsoluteStartBlockOfPhase(gen + numBlocksOfCycle + 1, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(second, service.getAbsoluteStartBlockOfPhase(gen + numBlocksOfCycle + numBlocksOfCycle - 1, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(third, service.getAbsoluteStartBlockOfPhase(gen + numBlocksOfCycle + numBlocksOfCycle, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
    }

    //TODO update with added periods
    @Test
    public void getCompensationRequestEndBlockTest() {
        // int chainHeight, int genesisHeight, int numBlocksOfCycle, int totalPeriodInBlocks
//        int blocks = Cycles.Phase.PROPOSAL.getCycleDuration(); //10
//        int numBlocksOfCycle = service.getNumBlocksOfCycle();
//        int gen = 1;
//        final int first = gen + blocks - 1; //10
//        final int second = first + numBlocksOfCycle; // 30
//        final int third = first + numBlocksOfCycle + numBlocksOfCycle; //40
//        assertEquals(first, service.getAbsoluteEndBlockOfPhase(0, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(first, service.getAbsoluteEndBlockOfPhase(gen, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(first, service.getAbsoluteEndBlockOfPhase(gen + 1, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(first, service.getAbsoluteEndBlockOfPhase(gen + numBlocksOfCycle - 1, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(second, service.getAbsoluteEndBlockOfPhase(gen + numBlocksOfCycle, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(second, service.getAbsoluteEndBlockOfPhase(gen + numBlocksOfCycle + 1, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(second, service.getAbsoluteEndBlockOfPhase(gen + numBlocksOfCycle + numBlocksOfCycle - 1, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
//        assertEquals(third, service.getAbsoluteEndBlockOfPhase(gen + numBlocksOfCycle + numBlocksOfCycle, gen, Cycles.Phase.PROPOSAL, numBlocksOfCycle));
    }

    //TODO update with added periods
    @Test
    public void getStartBlockOfPhaseTest() {
//        assertEquals(0, service.getNumBlocksOfPhaseStart(Cycles.Phase.PROPOSAL));
//        assertEquals(Cycles.Phase.PROPOSAL.getCycleDuration(),
//                service.getNumBlocksOfPhaseStart(Cycles.Phase.BREAK1));
    }

    @Test
    public void isInCurrentCycleTest() {
        //int txHeight, int chainHeight, int genesisHeight, int numBlocksOfCycle
//        int gen = 1;
//        int numBlocksOfCycle = service.getNumBlocksOfCycle();
//        assertFalse(service.isTxInCurrentCycle(gen, gen + numBlocksOfCycle, gen, numBlocksOfCycle));
//
//        assertFalse(service.isTxInCurrentCycle(gen - 1, gen - 1, gen, numBlocksOfCycle));
//        assertFalse(service.isTxInCurrentCycle(gen, gen - 1, gen, numBlocksOfCycle));
//        assertFalse(service.isTxInCurrentCycle(gen - 1, gen, gen, numBlocksOfCycle));
//        assertTrue(service.isTxInCurrentCycle(gen, gen, gen, numBlocksOfCycle));
//        assertTrue(service.isTxInCurrentCycle(gen, gen + 1, gen, numBlocksOfCycle));
//        assertTrue(service.isTxInCurrentCycle(gen, gen + numBlocksOfCycle - 1, gen, numBlocksOfCycle));
//
//        assertTrue(service.isTxInCurrentCycle(gen, gen + numBlocksOfCycle - 1, gen, numBlocksOfCycle));
//        assertTrue(service.isTxInCurrentCycle(gen + 1, gen + numBlocksOfCycle - 1, gen, numBlocksOfCycle));
//        assertTrue(service.isTxInCurrentCycle(gen + numBlocksOfCycle - 1, gen + numBlocksOfCycle - 1, gen, numBlocksOfCycle));
//        assertFalse(service.isTxInCurrentCycle(gen + numBlocksOfCycle, gen + numBlocksOfCycle - 1, gen, numBlocksOfCycle));
    }
}

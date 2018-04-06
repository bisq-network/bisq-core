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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;

import static com.google.common.base.Preconditions.checkArgument;

public class Cycles {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    // phase period: 30 days + 30 blocks
    public enum Phase {
        UNDEFINED,
        PROPOSAL,
        BREAK1,
        BLIND_VOTE,
        BREAK2,
        VOTE_REVEAL,
        BREAK3,
        ISSUANCE, // Must have only 1 block!
        BREAK4;
    }

    // The default cycle is used as the pre genesis cycle
    public class Cycle {
        // Durations of each phase
        @Getter
        List<Integer> phases = new ArrayList<>();

        @Getter
        private int startBlock = 0;
        @Getter
        private int endBlock = 0;

        Cycle(int startBlock) {
            this.startBlock = startBlock;
            // TODO: Set all phase durations using param service
            phases.add(0); // UNDEFINED Must be set to 0
            phases.add(2); // PROPOSAL
            phases.add(1); // BREAK1
            phases.add(2); // BLIND_VOTE
            phases.add(1); // BREAK2
            phases.add(2); // VOTE_REVEAL
            phases.add(1); // BREAK3
            phases.add(1); // ISSUANCE Must have only 1 block!
            phases.add(1); // BREAK4
            this.endBlock = startBlock + getCycleDuration();
        }

        public int getCycleDuration() {
            return getPhases().stream().mapToInt(Integer::intValue).sum();
        }

        public int getPhaseDuration(Phase phase) {
            return getPhases().get(phase.ordinal());
        }
    }

    private List<Cycle> cycles = new ArrayList<>();

    public Cycles(int genesisBlockHeight) {
        this.cycles.add(new Cycle(0));
        this.cycles.add(new Cycle(genesisBlockHeight));
    }

    // Return prehistoric cycle for blockHeight < genesis height (even for blockHeight < 0)
    Cycle getCycle(int blockHeight) {
        Optional<Cycle> cycle = cycles.stream().
                filter(c -> c.getStartBlock() <= blockHeight && blockHeight <= c.getEndBlock()).findAny();
        if (cycle.isPresent())
            return cycle.get();
        return cycles.get(0);
    }

    Phase getPhase(int blockHeight) {
        Cycle cycle = getCycle(blockHeight);
        int checkHeight = cycle.getStartBlock();
        List<Integer> phases = cycle.getPhases();
        for (int i = 0; i < phases.size(); ++i) {
            if (checkHeight + phases.get(i) > blockHeight)
                return Phase.values()[i];
            checkHeight += phases.get(i);
        }
        return Phase.UNDEFINED;
    }

    // Get start of phase given the blockheight of the cycle
    int getStartBlockOfPhase(int blockHeight, Phase phase) {
        Cycle cycle = getCycle(blockHeight);
        int checkHeight = cycle.getStartBlock();
        List<Integer> phases = cycle.getPhases();
        for (int i = 0; i < phases.size(); ++i) {
            if (checkHeight + phases.get(i) > blockHeight) {
                return checkHeight;
            }
            checkHeight += phases.get(i);
        }
        // Can't handle future phases until cycle is defined
        return 0;
    }

    // Get end of phase given the blockheight of the cycle
    int getEndBlockOfPhase(int blockHeight, Phase phase) {
        Cycle cycle = getCycle(blockHeight);
        int checkHeight = cycle.getStartBlock();
        List<Integer> phases = cycle.getPhases();
        for (int i = 0; i < phases.size(); ++i) {
            checkHeight += phases.get(i);
            if (checkHeight > blockHeight) {
                return checkHeight;
            }
        }
        // Can't handle future phases until cycle is defined
        return 0;
    }

    int getNumberOfStartedCycles() {
        return cycles.size();
    }

    public void onChainHeightChanged(int chainHeight) {
        Cycle lastCycle = cycles.get(cycles.size() - 1);
        while (chainHeight > lastCycle.getEndBlock()) {
            cycles.add(new Cycle(lastCycle.getEndBlock()));
            lastCycle = cycles.get(cycles.size() - 1);
        }
    }
}

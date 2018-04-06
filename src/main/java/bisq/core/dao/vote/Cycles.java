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
        private int undefined = 0;
        private int proposal = 2;
        private int break1 = 1;
        private int blindVote = 2;
        private int break2 = 1;
        private int voteReveal = 2;
        private int break3 = 1;
        private int issuance = 1; // Must have only 1 block!
        private int break4 = 1;

        @Getter
        private int startBlock = 0;
        @Getter
        private int endBlock = 0;

        Cycle(int startBlock) {
            this.startBlock = startBlock;
            // TODO: Set all phase durations using param service
            this.endBlock = startBlock + getCycleDuration();
        }

        List<Integer> getPhases() {
            List<Integer> list = new ArrayList<>();
            list.add(undefined);
            list.add(proposal);
            list.add(break1);
            list.add(blindVote);
            list.add(break2);
            list.add(voteReveal);
            list.add(break3);
            list.add(issuance);
            list.add(break4);
            return list;
        }

        public int getCycleDuration() {
            return getPhases().stream().mapToInt(Integer::intValue).sum();
        }

        public int getPhaseDuration(Phase phase) {
            return getPhases().get(phase.ordinal());
        }
    }

    private List<Cycle> cycles = new ArrayList<>();

    Cycles(int genesisBlockHeight) {
        this.cycles.add(new Cycle(0));
        this.cycles.add(new Cycle(genesisBlockHeight));
    }

    // Return prehistoric cycle for blockHeight < genesis height (even for blockHeight < 0)
    Cycle getCycle(int blockHeight) {
        Cycle cycle = cycles.get(0);
        for (Cycle c : cycles) {
            if (c.getStartBlock() > blockHeight)
                break;
            cycle = c;
        }
        return cycle;
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
                checkArgument(phase.ordinal() == i, "Phase must match");
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
                checkArgument(phase.ordinal() == i, "Phase must match");
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
        if (chainHeight > lastCycle.getEndBlock()) {
            cycles.add(new Cycle(lastCycle.getEndBlock()));
        }
    }
}

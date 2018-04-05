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
        @Setter
        private int undefined = 0;
        @Setter
        private int proposal = 2;
        @Setter
        private int break1 = 1;
        @Setter
        private int blindVote = 2;
        @Setter
        private int break2 = 1;
        @Setter
        private int voteReveal = 2;
        @Setter
        private int break3 = 1;
        @Setter
        private int issuance = 1; // Must have only 1 block!
        @Setter
        private int break4 = 1;

        @Getter
        @Setter
        private int startBlock = 0;

        Cycle() {
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
        this.cycles.add(new Cycle());
        Cycle genesis = new Cycle();
        genesis.setStartBlock(genesisBlockHeight);
        this.cycles.add(genesis);
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

    // TODO: This is a bit weird, why is blockheight and Phase needed?
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
}

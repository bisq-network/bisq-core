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

import lombok.Value;

@Value
public class Cycle {
    private final List<PhaseItem> phaseItems;
    private int heightOfFirstBlock;

    public Cycle(int heightOfFirstBlock) {
        this(heightOfFirstBlock, new ArrayList<>());
    }

    public Cycle(int heightOfFirstBlock, List<PhaseItem> phaseItems) {
        this.heightOfFirstBlock = heightOfFirstBlock;
        this.phaseItems = phaseItems;
    }

    public void setPhaseObject(PhaseItem phaseItem) {
        getPhaseObject(phaseItem.getPhase()).ifPresent(phaseItems::remove);
        phaseItems.add(phaseItem);
    }

    public Optional<PhaseItem> getPhaseObject(Phase phase) {
        return phaseItems.stream().filter(item -> item.getPhase() == phase).findAny();
    }

    public int getDuration(Phase phase) {
        return getPhaseObject(phase).map(PhaseItem::getDuration).orElse(0);
    }


    public int getDuration() {
        return phaseItems.stream().mapToInt(PhaseItem::getDuration).sum();
    }

    public int getHeightOfLastBlock() {
        return heightOfFirstBlock + getDuration() - 1;
    }

    public boolean isInPhase(int height, Phase phase) {
        return height >= getFirstBlockOfPhase(phase) &&
                height <= getLastBlockOfPhase(phase);
    }

    public int getFirstBlockOfPhase(Phase phase) {
        return heightOfFirstBlock + phaseItems.stream()
                .filter(item -> item.getPhase().ordinal() < phase.ordinal())
                .mapToInt(PhaseItem::getDuration).sum();
    }

    public int getLastBlockOfPhase(Phase phase) {
        return getFirstBlockOfPhase(phase) + getDuration(phase) - 1;
    }

    public int getDurationOfPhase(Phase phase) {
        return phaseItems.stream()
                .filter(item -> item.getPhase() == phase)
                .mapToInt(PhaseItem::getDuration).sum();
    }

    public Optional<Phase> getPhaseForHeight(int height) {
        return phaseItems.stream()
                .filter(item -> isInPhase(height, item.getPhase()))
                .map(PhaseItem::getPhase)
                .findAny();
    }

    @Override
    public String toString() {
        return "Cycle{" +
                "\n     phaseItems=" + phaseItems +
                ",\n     heightOfFirstBlock=" + heightOfFirstBlock +
                "\n}";
    }
}

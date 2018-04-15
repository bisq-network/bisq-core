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

import lombok.Value;

/**
 * We don't want to use a enum with the duration as field because the duration can change and enums are usually
 * considered immutable.
 */
@Value
public class PhaseItem {
    private final Phase phase;
    private final int duration;

    public PhaseItem(Phase phase, int duration) {
        this.phase = phase;
        this.duration = duration;
    }
}

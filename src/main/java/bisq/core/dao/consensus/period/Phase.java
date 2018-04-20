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

/**
 * Enum for phase of a cycle.
 *
 * We don't want to use a enum with the duration as field because the duration can change by voting and enums
 * should be considered immutable.
 */
public enum Phase {
    UNDEFINED,
    PROPOSAL,
    BREAK1,
    BLIND_VOTE,
    BREAK2,
    VOTE_REVEAL,
    BREAK3,
    VOTE_RESULT,
    BREAK4
}

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

package bisq.core.dao.consensus.state.events;

import bisq.common.proto.persistable.PersistablePayload;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.concurrent.Immutable;

@Immutable
@Getter
@EqualsAndHashCode
public abstract class StateChangeEvent implements PersistablePayload {
    private final PersistablePayload data;
    private final int height; //TODO needed? is part of block anyway

    public StateChangeEvent(PersistablePayload data, int height) {
        this.data = data;
        this.height = height;
    }

    //TODO add PB StateChangeEvent.builder
}

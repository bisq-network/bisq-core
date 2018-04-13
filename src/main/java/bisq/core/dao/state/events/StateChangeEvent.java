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

package bisq.core.dao.state.events;

import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.proto.persistable.PersistablePayload;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public abstract class StateChangeEvent implements PersistablePayload {
    private int height;
    private final ProtectedStoragePayload payload;

    public StateChangeEvent(ProtectedStoragePayload payload, int height) {
        this.payload = payload;
        this.height = height;
    }
}

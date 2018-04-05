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

package bisq.core.dao.consensus;

import java.util.Optional;

import lombok.Getter;

/**
 * Provides byte constants for distinguishing the type of a DAO transaction used in the OP_RETURN data.
 */

public enum OpReturnType {
    PROPOSAL((byte) 0x02),
    COMPENSATION_REQUEST((byte) 0x01),
    BLIND_VOTE((byte) 0x03),
    VOTE_REVEAL((byte) 0x04),
    LOCK_UP((byte) 0x05),
    UNLOCK((byte) 0x06);

    @Getter
    private byte type;

    OpReturnType(byte type) {
        this.type = type;
    }

    public static Optional<OpReturnType> getOpReturnType(byte type) {
        if (type == PROPOSAL.getType())
            return Optional.of(PROPOSAL);
        else if (type == COMPENSATION_REQUEST.getType())
            return Optional.of(COMPENSATION_REQUEST);
        else if (type == BLIND_VOTE.getType())
            return Optional.of(BLIND_VOTE);
        else if (type == VOTE_REVEAL.getType())
            return Optional.of(VOTE_REVEAL);
        else if (type == LOCK_UP.getType())
            return Optional.of(LOCK_UP);
        else if (type == UNLOCK.getType())
            return Optional.of(UNLOCK);
        else
            return Optional.empty();
    }
}

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

import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Provides byte constants for distinguishing the type of a DAO transaction used in the OP_RETURN data.
 */

public enum OpReturnType {
    COMPENSATION_REQUEST((byte) 0x01),
    PROPOSAL((byte) 0x02),
    BLIND_VOTE((byte) 0x03),
    VOTE_REVEAL((byte) 0x04),
    LOCK_UP((byte) 0x05),
    UNLOCK((byte) 0x06);

    @Getter
    private byte type;

    OpReturnType(byte type) {
        this.type = type;
    }

    @Nullable
    public static OpReturnType getOpReturnType(byte type) {
        if (type == COMPENSATION_REQUEST.getType())
            return COMPENSATION_REQUEST;
        else if (type == PROPOSAL.getType())
            return PROPOSAL;
        else if (type == BLIND_VOTE.getType())
            return BLIND_VOTE;
        else if (type == VOTE_REVEAL.getType())
            return VOTE_REVEAL;
        else if (type == LOCK_UP.getType())
            return LOCK_UP;
        else if (type == UNLOCK.getType())
            return UNLOCK;
        else
            return null;
    }
}

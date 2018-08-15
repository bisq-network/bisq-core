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

package bisq.core.dao.node.parser;

import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.bonding.lockup.LockupType;

import bisq.common.util.Utilities;

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class OpReturnLockupParser {

    @Inject
    public OpReturnLockupParser() {
    }

    // We do not check the version as if we upgrade the a new version old clients would fail. Rather we need to make
    // a change backward compatible so that new clients can handle both versions and old clients are tolerant.
    boolean validate(byte[] opReturnData, Optional<LockupType> lockupType, int lockTime, int blockHeight,
                     ParsingModel parsingModel) {
        // TODO: Handle all lockupTypes
        if (!lockupType.isPresent()) {
            log.warn("No lockupType found for lockup tx, opReturnData=" + Utilities.encodeToHex(opReturnData));
            return false;
        }

        return parsingModel.getLockupOutput() != null &&
                opReturnData.length == 25 &&
                lockTime >= BondingConsensus.getMinLockTime() &&
                lockTime <= BondingConsensus.getMaxLockTime();
    }
}

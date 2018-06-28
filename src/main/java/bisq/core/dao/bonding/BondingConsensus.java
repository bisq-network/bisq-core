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

package bisq.core.dao.bonding;

import bisq.core.dao.bonding.lockup.LockupType;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.Util;

import bisq.common.app.Version;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BondingConsensus {
    // TODO  add choice of sub version for lock type (e.g. roles, trade,...)
    public static byte[] getLockupOpReturnData(int lockTime) throws IOException {
        // Pushdata of <= 4 bytes is converted to int when returned from bitcoind and not handled the way we
        // require by btcd-cli4j, avoid opreturns with 4 bytes or less
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(OpReturnType.LOCKUP.getType());
            outputStream.write(Version.LOCKUP_VERSION);
            outputStream.write(LockupType.DEFAULT.getType());
            Util.writeOpReturnData(outputStream, lockTime, 2);
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Not expected to happen ever
            e.printStackTrace();
            log.error(e.toString());
            throw e;
        }
    }
}

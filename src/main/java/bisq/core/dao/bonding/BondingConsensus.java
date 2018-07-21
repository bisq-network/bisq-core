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

import java.util.Arrays;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BondingConsensus {
    // In the UI we don't allow 0 as that would mean that the tx gets spent
    // in the same block as the unspent tx and we don't support unconfirmed txs in the DAO. Technically though 0
    // works as well.
    @Getter
    private static int minLockTime = 1;

    // Max value is max of a short int as we use only 2 bytes in the opReturn for the lockTime
    @Getter
    private static int maxLockTime = 65535;

    // TODO SQ: add choice of sub version for lock type (e.g. roles, trade,...)
    public static byte[] getLockupOpReturnData(int lockTime, LockupType type, byte[] hash) throws IOException {
        // PushData of <= 4 bytes is converted to int when returned from bitcoind and not handled the way we
        // require by btcd-cli4j, avoid opReturns with 4 bytes or less
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(OpReturnType.LOCKUP.getType());
            outputStream.write(Version.LOCKUP);
            outputStream.write(type.getType());
            Util.writeOpReturnData(outputStream, lockTime, 2);
            if (hash != null)
                outputStream.write(hash);
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Not expected to happen ever
            e.printStackTrace();
            log.error(e.toString());
            throw e;
        }
    }

    public static boolean isLockTimeOver(long unlockBlockHeight, long currentBlockHeight) {
        return currentBlockHeight >= unlockBlockHeight;
    }

    // Bond id is the 20 byte hash added to opreturn for lockup tx that require an id
    public static byte[] getBondId(byte[] opReturn) {
        return opReturn.length == 25 ? Arrays.copyOfRange(opReturn, 5, 25) : new byte[0];
    }
}

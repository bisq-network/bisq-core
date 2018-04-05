/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.vote.proposal.compensation;

import bisq.core.dao.consensus.OpReturnType;

import bisq.common.app.Version;

import org.bitcoinj.core.Coin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompensationRequestConsensus {
    public static Coin getMinCompensationRequestAmount() {
        return Coin.valueOf(5_000); // 50 BSQ
    }

    public static Coin getMaxCompensationRequestAmount() {
        return Coin.valueOf(5_000_000); // 50 000 BSQ
    }

    public static byte[] getOpReturnData(byte[] hashOfPayload) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(OpReturnType.COMPENSATION_REQUEST.getType());
            outputStream.write(Version.COMPENSATION_REQUEST_VERSION);
            outputStream.write(hashOfPayload);
            return outputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.toString());
            throw e;
        }
    }
}

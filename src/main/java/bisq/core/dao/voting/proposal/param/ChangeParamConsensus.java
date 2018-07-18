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

package bisq.core.dao.voting.proposal.param;

import bisq.core.dao.state.blockchain.OpReturnType;

import bisq.common.app.Version;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class ChangeParamConsensus {
    public static byte[] getOpReturnData(byte[] hashOfPayload) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(OpReturnType.PROPOSAL.getType());
            outputStream.write(Version.PROPOSAL);
            outputStream.write(hashOfPayload);
            return outputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.toString());
            throw e;
        }
    }
}

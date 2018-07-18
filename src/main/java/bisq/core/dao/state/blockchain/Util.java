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

package bisq.core.dao.state.blockchain;

import java.io.ByteArrayOutputStream;

public class Util {
    // Write the lower 'bytes' bytes of data
    public static void writeOpReturnData(ByteArrayOutputStream outputStream, int data, int bytes) {
        for (int i = bytes - 1; i >= 0; --i) {
            // write() only writes the lowest byte of the int to the stream
            outputStream.write(data >>> (8 * i));
        }
    }

    public static int parseAsInt(byte[] opReturnData) {
        int result = 0;
        for (byte anOpReturnData : opReturnData) {
            result = (result << 8) | anOpReturnData;
        }
        return result;
    }
}

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

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class LitecoinExtremeTest extends AbstractAssetTest {

    public LitecoinExtremeTest() {
        super(new LitecoinExtreme());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("ERqtZ59nx6VHFmS2haJb4ZbCwfWwBLQnfH");
        assertValidAddress("ES8ctKPznojkkJ9bzakSZgfyxrvqGPS7PV");
        assertValidAddress("EabRC9NJNhybs3bEK5kZWbG5pTwAdcuo9H");
        assertValidAddress("EcLEQvpiowww84JPXFv5MHBe7Eza9zohQd");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("0EeEKXUi87uAtkVa131ERLMZwQ79AQBi9hp");
        assertInvalidAddress("3EYo8cKi4zqCq2vV1EjnGF4WrxaSjNUdiih");
        assertInvalidAddress("1Ee6q9hzjW75Y69ytGVnBTfU4tjTJuppwfe");
    }
}

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
        assertValidAddress("ETzxRKVgGyRFBaCBHeFvCngND4ExfMGpr9");
        assertValidAddress("EKmKU1a16qGxA5AAyH6u84ngVuPnbLZnno");
        assertValidAddress("EWzk3QbP71TSZRQn5VCDP8aHYbtb8je6QL");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1ERCzw5j5u8rzYZAafGCgbeFL7NEX95AMmy");
        assertInvalidAddress("GEdrFRoZiMKfVy7Tpb4MhhM7u4vaJJBDffb");
        assertInvalidAddress("hEHjsXcL1EccbDd51vtpNauTGBK7zeNg6Dr");
    }
}

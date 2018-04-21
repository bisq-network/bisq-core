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
        assertValidAddress("EPQoqfEwpQn9NzrxrBfW1wN2tLQ1WvgfYs");
        assertValidAddress("EWWWXsn83DEaiyKiWQM9PPwuBccd26SyFz");
        assertValidAddress("EM4F4p2ZCiHh1dQCbqy15ZaQFaPPJkTUv3");
        assertValidAddress("EdZxv4MpLurXyRyysvH756zKBAr5ujretk");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1EVVviyVkgWy1rzQ9i35iRp9C2nyCP1W9Ji");
        assertInvalidAddress("GKSdsTcVKMVUtAnR2szzh8ABqyMrngNMZ7");
        assertInvalidAddress("TbPH5VvRRWxxwAKy4oQw3tU5EPPMBZmVkY");
    }
}

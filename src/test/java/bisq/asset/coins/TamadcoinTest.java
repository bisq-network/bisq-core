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

public class TamadcoinTest extends AbstractAssetTest {

    public TamadcoinTest() {
        super(new Tamadcoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("axRehEfp5BjgHsZKXBECdDgLZPrgmCaXxKxy7P8Z6J3qDbJcQK5v9fDc3SBHoiuQCYa6aUoARJ3b7FyeGHKDJLoq2es58GXi4");
        assertValidAddress("axRpWzdirPe7njtxevGCzhUoDZnsa3ncdHyiebYcDqGwXR6R5CyAa79c3SBHoiuQCYa6aUoARJ3b7FyeGHKDJLoq2es9JwJXd");
        assertValidAddress("axR311K1J6GRmCXmFaegZdc7v4Nv5f3c5Hv6bK84txRMGqeNbuVrr15c3SBHoiuQCYa6aUoARJ3b7FyeGHKDJLoq2es5dqYJy");
        assertValidAddress("axRSb2PovsKLS4JaVXEdH4gQfEhCLFcykYUnsRPtxa4Y89nZZrbRtYXc3SBHoiuQCYa6aUoARJ3b7FyeGHKDJLoq2es5s9k2F");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("axrehEfp5BjgHsZKXBECdDgLZPrgmCaXxKxy7P8Z6J3qDbJcQK5v9fDc3SBHoiuQCYa6aUoARJ3b7Fye");
        assertInvalidAddress("AxRpWzdirPe7njtxevGCzhUoDZnsa3ncdHyiebYcDqGwXR6");
        assertInvalidAddress("axss311K1J6GRmCXmFaegZdc7v4Nv5f3c5Hv6bK84txRMGqeNbuVrr15c3SBHoiuQCYa6aUoARJ3b7FyeGHKDJLoq2es5dqYJy");
        assertInvalidAddress("axaSb2PovsKLS4JaVXEdH4gQfEhCLFcykYUnsRPtxa4Y89nZZrbRtYXc3SBHoiuQCYa6aUoARJ3b7FyeGHKDJLoq2es5s9k2F");
        
    }
}

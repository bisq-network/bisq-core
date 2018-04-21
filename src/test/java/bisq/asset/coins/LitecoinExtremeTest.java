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
        super(new BitcoinCash());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("EMaYcKYVEBQ4tePFP46hWvPpYqyaReSERf");
        assertValidAddress("ET9wsr5KB49naNLPEhp3HyRGt4VELjUzfm");
        assertValidAddress("EK4xxy7FfAMWJDBxbsEAVJW2trQFuPRbD9");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("2EHQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHa");
        assertInvalidAddress("1EQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHs");
        assertInvalidAddress("EMaYcKYVEBQ4tePFP46hWvPpYqyaReSERf#");
    }
}

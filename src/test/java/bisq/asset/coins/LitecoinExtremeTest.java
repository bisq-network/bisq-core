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
        super(new Dogecoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("EfQcUN2FUVwuV3aeSnyqbkVMEu2Zfc7zsA");
        assertValidAddress("EfQcUN2FUVwuV3aeSnyqbkVMEu2Zfc7zsA");
        assertValidAddress("ELYV82RoBd6jRZg6GD5ATjnJ24cdAqawQM");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("DEJoC4Pwnsqn98NHphdYjw8FRcNyeNjoXHu");
        assertInvalidAddress("FELYV82RoBd6jRZg6GD5ATjnJ24cdAqawQM");
        assertInvalidAddress("EfsnAs8Dqvtykm2JKn8pXRWJBi3etbg9th#");
    }
}

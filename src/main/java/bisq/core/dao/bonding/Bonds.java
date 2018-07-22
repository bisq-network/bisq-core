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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.Getter;


public class Bonds {
    @Getter
    private static List<Bond> bonds = Arrays.asList(
            new Bond("Chris Beams", BondedRole.ARBITRATOR),
            new Bond("Manfred Karrer", BondedRole.DOMAIN_NAME_HOLDER),
            new Bond("SQ", BondedRole.SEED_NODE_OPERATOR)
    );

    public static Optional<Bond> getBond(byte[] hashOfBondId) {
        return bonds.stream().filter(bond -> Arrays.equals(bond.getHash(), hashOfBondId)).findAny();
    }
}

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

import bisq.core.locale.Res;

import bisq.common.crypto.Hash;

import com.google.common.base.Charsets;

import lombok.Data;

@Data
public class Bond {
    private String name;
    private BondedRole role;

    public Bond(String name, BondedRole role) {
        this.name = name;
        this.role = role;
    }

    public byte[] getHash() {
        return Hash.getSha256Ripemd160hash((name + role.name()).getBytes(Charsets.UTF_8));
    }

    public String toDisplayString() {
        return name + " / " + Res.get("dao.bonding.lock.bond." + role.name());
    }
}

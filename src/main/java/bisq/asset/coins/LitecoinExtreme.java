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

import bisq.asset.Base58BitcoinAddressValidator;
import bisq.asset.Coin;

import org.libdohj.params.LitecoinExtremeMainNetParams;
import org.libdohj.params.LitecoinExtremeRegTestParams;
import org.libdohj.params.LitecoinExtremeTestNet3Params;

import org.bitcoinj.core.NetworkParameters;

public abstract class LitecoinExtreme extends Coin {

    public LitecoinExtreme(Network network, NetworkParameters networkParameters) {
        super("LitecoinExtreme", "LTC", new Base58BitcoinAddressValidator(networkParameters), network);
    }


    public static class Mainnet extends LitecoinExtreme {

        public Mainnet() {
            super(Network.MAINNET, LitecoinExtremeMainNetParams.get());
        }
    }


    public static class Testnet extends LitecoinExtreme {

        public Testnet() {
            super(Network.TESTNET, LitecoinExtremeTestNet3Params.get());
        }
    }


    public static class Regtest extends LitecoinExtreme {

        public Regtest() {
            super(Network.REGTEST, LitecoinExtremeRegTestParams.get());
        }
    }
}

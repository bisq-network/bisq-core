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

package bisq.asset;

import bisq.core.btc.BaseCurrencyNetwork;
import bisq.core.locale.Res;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public abstract class AbstractAssetTest {

    protected final Asset asset;

    public AbstractAssetTest(Asset asset) {
        this.asset = asset;
    }

    @Before
    public void setup() {
        BaseCurrencyNetwork baseCurrencyNetwork = BaseCurrencyNetwork.BTC_MAINNET;
        Res.setBaseCurrencyCode(baseCurrencyNetwork.getCurrencyCode());
        Res.setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
    }

    @Test
    public void testBlank() {
        assertInvalidAddress("");
    }

    @Test
    public abstract void testValidAddresses();

    @Test
    public abstract void testInvalidAddresses();

    protected void assertValidAddress(String address) {
        AddressValidationResult result = asset.validateAddress(address);
        assertThat(result.getMessage(), result.isValid(), is(true));
    }

    protected void assertInvalidAddress(String address) {
        assertThat(asset.validateAddress(address).isValid(), is(false));
    }
}

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

import static org.apache.commons.lang3.Validate.notBlank;
import static org.apache.commons.lang3.Validate.notNull;

public abstract class AbstractAsset implements Asset {

    private final String name;
    private final String tickerSymbol;
    private final AddressValidator addressValidator;

    public AbstractAsset(String name, String tickerSymbol, AddressValidator addressValidator) {
        this.name = notBlank(name);
        this.tickerSymbol = notBlank(tickerSymbol);
        this.addressValidator = notNull(addressValidator);
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String getTickerSymbol() {
        return tickerSymbol;
    }

    @Override
    public final AddressValidationResult validateAddress(String address) {
        return addressValidator.validate(address);
    }
}

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

public class AddressValidationResult {

    public static AddressValidationResult VALID = new AddressValidationResult(true);

    private final boolean isValid;
    private String message;
    private String i18nKey;

    private AddressValidationResult(boolean isValid) {
        this.isValid = isValid;
    }

    private AddressValidationResult(boolean isValid, String message, String i18nKey) {
        this(isValid);
        this.message = message;
        this.i18nKey = i18nKey;
    }

    public boolean isValid() {
        return isValid;
    }

    public String getI18nKey() {
        return i18nKey;
    }

    public String getMessage() {
        return message;
    }

    public static AddressValidationResult validAddress() {
        return VALID;
    }

    public static AddressValidationResult invalidStructure() {
        return new AddressValidationResult(false, "", "validation.altcoin.wrongStructure");
    }

    public static AddressValidationResult invalidAddress(String cause) {
        return new AddressValidationResult(false, cause, "validation.altcoin.invalidAddress");
    }

    public static AddressValidationResult invalidAddress(Throwable cause) {
        return invalidAddress(cause.getMessage());
    }
}

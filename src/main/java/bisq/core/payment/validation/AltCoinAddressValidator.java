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

package bisq.core.payment.validation;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.BaseCurrencyNetwork;
import bisq.core.locale.Res;
import bisq.core.payment.validation.altcoins.KOTOAddressValidator;
import bisq.core.payment.validation.altcoins.WMCCAddressValidator;
import bisq.core.payment.validation.params.ACHParams;
import bisq.core.payment.validation.params.AlcParams;
import bisq.core.payment.validation.params.PhoreParams;
import bisq.core.payment.validation.params.StrayaParams;
import bisq.core.payment.validation.params.WMCCParams;
import bisq.core.util.validation.InputValidator;

import bisq.asset.AddressValidationResult;
import bisq.asset.Asset;
import bisq.asset.AssetRegistry;
import bisq.asset.Coin;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;

import com.google.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
public final class AltCoinAddressValidator extends InputValidator {

    private final AssetRegistry assetRegistry;
    private String currencyCode;

    @Inject
    public AltCoinAddressValidator(AssetRegistry assetRegistry) {
        this.assetRegistry = assetRegistry;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    @Override
    public ValidationResult validate(String input) {
        ValidationResult validationResult = super.validate(input);
        if (!validationResult.isValid || currencyCode == null) {
            return validationResult;
        } else {
            ValidationResult wrongChecksum = new ValidationResult(false,
                    Res.get("validation.altcoin.wrongChecksum"));
            ValidationResult regexTestFailed = new ValidationResult(false,
                    Res.get("validation.altcoin.wrongStructure", currencyCode));

            Optional<Asset> asset = assetRegistry.stream()
                    .filter(this::assetMatchesSelectedCurrencyCode)
                    .filter(this::assetIsNotBaseCurrencyForDifferentNetwork)
                    .findFirst();

            if (asset.isPresent()) {
                AddressValidationResult addressValidationResult = asset.get().validateAddress(input);

                if (addressValidationResult.isValid())
                    return new ValidationResult(true);

                return new ValidationResult(false, Res.get(addressValidationResult.getI18nKey(), asset.get().getTickerSymbol(), addressValidationResult.getMessage()));
            }

            switch (currencyCode) {
                case "SOS":
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "ACH":
                    try {
                        Address.fromBase58(ACHParams.get(), input);
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
                case "VDN":
                    if (!input.matches("^[D][0-9a-zA-Z]{33}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "ALC":
                    if (input.matches("^[A][a-km-zA-HJ-NP-Z1-9]{25,34}$")) {
                        //noinspection ConstantConditions
                        try {
                            Address.fromBase58(AlcParams.get(), input);
                            return new ValidationResult(true);
                        } catch (AddressFormatException e) {
                            return new ValidationResult(false, getErrorMessage(e));
                        }
                    } else {
                        return regexTestFailed;
                    }
                case "DIN":
                    if (!input.matches("^[D][0-9a-zA-Z]{33}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "NAH":
                    if (input.matches("^[S][a-zA-Z0-9]{26,34}$")) {
                        //noinspection ConstantConditions
                        try {
                            Address.fromBase58(StrayaParams.get(), input);
                            return new ValidationResult(true);
                        } catch (AddressFormatException e) {
                            return new ValidationResult(false, getErrorMessage(e));
                        }
                    } else {
                        return regexTestFailed;
                    }
                case "ROI":
                    if (!input.matches("^[R][0-9a-zA-Z]{33}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "WMCC":
                    return WMCCAddressValidator.ValidateAddress(WMCCParams.get(), input);
                case "RTO":
                    if (!input.matches("^[A][0-9A-Za-z]{94}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "KOTO":
                    return KOTOAddressValidator.ValidateAddress(input);
                case "UBQ":
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "QWARK":
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "GEO":
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "GRANS":
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "PHR":
                    if (input.matches("^[P][a-km-zA-HJ-NP-Z1-9]{25,34}$")) {
                        //noinspection ConstantConditions
                        try {
                            Address.fromBase58(PhoreParams.get(), input);
                            return new ValidationResult(true);
                        } catch (AddressFormatException e) {
                            return new ValidationResult(false, getErrorMessage(e));
                        }
                    } else {
                        return regexTestFailed;
                    }

                    // Add new coins at the end...
                default:
                    log.debug("Validation for AltCoinAddress not implemented yet. currencyCode: " + currencyCode);
                    return validationResult;
            }
        }
    }

    @NotNull
    private String getErrorMessage(AddressFormatException e) {
        return Res.get("validation.altcoin.invalidAddress", currencyCode, e.getMessage());
    }

    private boolean assetMatchesSelectedCurrencyCode(Asset a) {
        return currencyCode.equals(a.getTickerSymbol());
    }

    private boolean assetIsNotBaseCurrencyForDifferentNetwork(Asset asset) {
        BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();

        return !(asset instanceof Coin)
                || !asset.getTickerSymbol().equals(baseCurrencyNetwork.getCurrencyCode())
                || (((Coin) asset).getNetwork().name().equals(baseCurrencyNetwork.getNetwork()));
    }

}

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
import bisq.core.payment.validation.altcoins.YTNAddressValidator;
import bisq.core.payment.validation.params.ACHParams;
import bisq.core.payment.validation.params.AlcParams;
import bisq.core.payment.validation.params.CageParams;
import bisq.core.payment.validation.params.CreaParams;
import bisq.core.payment.validation.params.ODNParams;
import bisq.core.payment.validation.params.OnionParams;
import bisq.core.payment.validation.params.PARTParams;
import bisq.core.payment.validation.params.PhoreParams;
import bisq.core.payment.validation.params.SpeedCashParams;
import bisq.core.payment.validation.params.StrayaParams;
import bisq.core.payment.validation.params.WMCCParams;
import bisq.core.payment.validation.params.XspecParams;
import bisq.core.payment.validation.params.btc.BTGParams;
import bisq.core.payment.validation.params.btc.BtcMainNetParamsForValidation;
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
                case "INXT":
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "PART":
                    if (input.matches("^[RP][a-km-zA-HJ-NP-Z1-9]{25,34}$")) {
                        //noinspection ConstantConditions
                        try {
                            Address.fromBase58(PARTParams.get(), input);
                            return new ValidationResult(true);
                        } catch (AddressFormatException e) {
                            return new ValidationResult(false, getErrorMessage(e));
                        }
                    } else {
                        return regexTestFailed;
                    }
                case "MDC":
                    if (input.matches("^m[a-zA-Z0-9]{26,33}$"))
                        return new ValidationResult(true);
                    else
                        return regexTestFailed;
                case "BCHC":
                    try {
                        Address.fromBase58(BtcMainNetParamsForValidation.get(), input);
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
                case "BTG":
                    try {
                        Address.fromBase58(BTGParams.get(), input);
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
                case "CAGE":
                    if (input.matches("^[D][a-zA-Z0-9]{26,34}$")) {
                        //noinspection ConstantConditions
                        try {
                            Address.fromBase58(CageParams.get(), input);
                            return new ValidationResult(true);
                        } catch (AddressFormatException e) {
                            return new ValidationResult(false, getErrorMessage(e));
                        }
                    } else {
                        return regexTestFailed;
                    }
                case "CRED":
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "XSPEC":
                    try {
                        Address.fromBase58(XspecParams.get(), input);
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
                case "WILD":
                    // https://github.com/ethereum/web3.js/blob/master/lib/utils/utils.js#L403
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "ONION":
                    try {
                        Address.fromBase58(OnionParams.get(), input);
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
                case "CREA":
                    try {
                        Address.fromBase58(CreaParams.get(), input);
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
                case "XIN":
                    if (!input.matches("^XIN-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{5}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "BETR":
                    // https://github.com/ethereum/web3.js/blob/master/lib/utils/utils.js#L403
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "MVT":
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "REF":
                    // https://github.com/ethereum/web3.js/blob/master/lib/utils/utils.js#L403
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "STL":
                    if (!input.matches("^(Se)\\d[0-9A-Za-z]{94}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "DAI":
                    // https://github.com/ethereum/web3.js/blob/master/lib/utils/utils.js#L403
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "YTN":
                    return YTNAddressValidator.ValidateAddress(input);
                case "DARX":
                    if (!input.matches("^[R][a-km-zA-HJ-NP-Z1-9]{25,34}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                case "ODN":
                    try {
                        Address.fromBase58(ODNParams.get(), input);
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
                case "CDT":
                    if (input.startsWith("D"))
                        return new ValidationResult(true);
                    else
                        return new ValidationResult(false);
                case "DGM":
                    if (input.matches("^[D-E][a-zA-Z0-9]{33}$"))
                        return new ValidationResult(true);
                    else
                        return regexTestFailed;
                case "SCS":
                    try {
                        Address.fromBase58(SpeedCashParams.get(), input);
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
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

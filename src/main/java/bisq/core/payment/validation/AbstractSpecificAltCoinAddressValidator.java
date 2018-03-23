package bisq.core.payment.validation;

import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;
import org.bitcoinj.core.AddressFormatException;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSpecificAltCoinAddressValidator implements SpecificAltCoinAddressValidator {

    protected InputValidator.ValidationResult getRegexTestFailed() {
        return new InputValidator.ValidationResult(false, Res.get("validation.altcoin.wrongStructure", getCurrencyCode()));
    }

    @NotNull
    protected String getErrorMessage(AddressFormatException e) {
        return Res.get("validation.altcoin.invalidAddress", getCurrencyCode(), e.getMessage());
    }
}

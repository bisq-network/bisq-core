package bisq.core.payment.validation.altcoins.ella;

import bisq.core.payment.validation.AbstractSpecificAltCoinAddressValidator;
import bisq.core.util.validation.InputValidator;

public class EllaAddressValidator extends AbstractSpecificAltCoinAddressValidator {

    @Override
    public String getCurrencyCode() {
        return "ELLA";
    }

    @Override
    public String getCurrencyName() {
        return "Ellaism";
    }

    @Override
    public boolean isAsset() {
        return false;
    }

    @Override
    public InputValidator.ValidationResult validate(String input) {
        // https://github.com/ethereum/web3.js/blob/master/lib/utils/utils.js#L403
        if (!input.matches("^(0x)?[0-9a-fA-F]{40}$")) {
            return getRegexTestFailed();
        } else {
            return new InputValidator.ValidationResult(true);
        }
    }
}

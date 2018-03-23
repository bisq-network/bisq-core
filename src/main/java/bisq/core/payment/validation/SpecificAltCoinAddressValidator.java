package bisq.core.payment.validation;

import bisq.core.util.validation.InputValidator;

public interface SpecificAltCoinAddressValidator {

    String getCurrencyCode();

    String getCurrencyName();

    boolean isAsset();

    InputValidator.ValidationResult validate(String input);
}

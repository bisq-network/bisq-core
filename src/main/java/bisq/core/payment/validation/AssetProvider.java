package bisq.core.payment.validation;

import bisq.core.util.validation.InputValidator;

public interface AssetProvider {

    String getCurrencyCode();

    String getCurrencyName();

    boolean isAsset();

    InputValidator.ValidationResult validateAddress(String input);
}

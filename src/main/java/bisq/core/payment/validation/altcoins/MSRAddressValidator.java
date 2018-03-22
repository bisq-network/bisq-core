package bisq.core.payment.validation.altcoins;

import bisq.core.util.validation.InputValidator;

public class MSRAddressValidator{
    /**
     * Check if a string represent a valid Masari wallet address or not
     *
     * @param addr the probable Masari address
     * @return a valid InputValidator.ValidationResult if the Masari address is validd, an invalid otherwise with an
     * error detail
     */
    public static InputValidator.ValidationResult ValidateAddress(String addr) {
        if (addr.length() != 95)
            return new InputValidator.ValidationResult(false, "MSR_Addr_Invalid: Length must be 95!");
        if (!addr.startsWith("5"))
            return new InputValidator.ValidationResult(false, "MSR_Addr_Invalid: must start with '5'!");
        if (!addr.matches("^[5][0-9A-Za-z]{94}$"))
            return new InputValidator.ValidationResult(false, "MSR_Addr_Invalid: does not match basic regex");

        return new InputValidator.ValidationResult(true);
    }
}

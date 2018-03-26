package bisq.core.payment.validation.altcoins.bch;

import bisq.core.payment.validation.AbstractAssetProvider;
import bisq.core.payment.validation.params.btc.BtcMainNetParamsForValidation;
import bisq.core.util.validation.InputValidator;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;

public class BchAssetProvider extends AbstractAssetProvider {

    @Override
    public String getCurrencyCode() {
        return "BCH";
    }

    @Override
    public String getCurrencyName() {
        return "Bitcoin Cash";
    }

    @Override
    public boolean isAsset() {
        return false;
    }

    @Override
    public InputValidator.ValidationResult validateAddress(String input) {
        try {
            Address.fromBase58(BtcMainNetParamsForValidation.get(), input);
            return new InputValidator.ValidationResult(true);
        } catch (AddressFormatException e) {
            return new InputValidator.ValidationResult(false, getErrorMessage(e));
        }
    }
}

package bisq.core.payment.validation;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.BaseCurrencyNetwork;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;

public abstract class AbstractAltcoinAddressValidatorTest {

    @Before
    public void setup() {
        final BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        final String currencyCode = baseCurrencyNetwork.getCurrencyCode();
        Res.setBaseCurrencyCode(currencyCode);
        Res.setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
        CurrencyUtil.setBaseCurrencyCode(currencyCode);
    }

    @NotNull
    protected AltCoinAddressValidator getAltCoinAddressValidator() {
        return new AltCoinAddressValidator(AssetProviderRegistry.getInstance());
    }
}

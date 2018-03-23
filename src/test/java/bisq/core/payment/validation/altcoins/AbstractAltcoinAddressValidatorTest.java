package bisq.core.payment.validation.altcoins;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.BaseCurrencyNetwork;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
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
}

package bisq.core.payment.validation;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.BaseCurrencyNetwork;
import bisq.asset.AssetRegistry;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;

import org.junit.Before;

public abstract class AbstractAltcoinAddressValidatorTest {

    protected AltCoinAddressValidator validator = new AltCoinAddressValidator(new AssetRegistry());

    @Before
    public void setup() {
        BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        String currencyCode = baseCurrencyNetwork.getCurrencyCode();
        Res.setBaseCurrencyCode(currencyCode);
        Res.setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
        CurrencyUtil.setBaseCurrencyCode(currencyCode);
    }
}

package bisq.asset;

import static org.apache.commons.lang3.Validate.notBlank;
import static org.apache.commons.lang3.Validate.notNull;

public abstract class AbstractAsset implements Asset {

    private final String name;
    private final String tickerSymbol;
    private final AddressValidator addressValidator;

    public AbstractAsset(String name, String tickerSymbol, AddressValidator addressValidator) {
        this.name = notBlank(name);
        this.tickerSymbol = notBlank(tickerSymbol);
        this.addressValidator = notNull(addressValidator);
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String getTickerSymbol() {
        return tickerSymbol;
    }

    @Override
    public final AddressValidationResult validateAddress(String address) {
        return addressValidator.validate(address);
    }
}

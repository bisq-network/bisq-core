package bisq.asset;

public interface Asset {

    String getName();

    String getTickerSymbol();

    AddressValidationResult validateAddress(String address);
}

package bisq.core.offer;

public class MarketPriceNotAvailableException extends Exception {
    public MarketPriceNotAvailableException(@SuppressWarnings("SameParameterValue") String message) {
        super(message);
    }
}

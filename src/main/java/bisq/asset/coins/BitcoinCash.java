package bisq.asset.coins;

import bisq.asset.Base58BitcoinAddressValidator;
import bisq.asset.Coin;

public class BitcoinCash extends Coin {

    public BitcoinCash() {
        super("Bitcoin Cash", "BCH", new Base58BitcoinAddressValidator());
    }
}

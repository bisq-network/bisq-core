package bisq.asset.coins;

import bisq.asset.Coin;
import bisq.asset.RegexAddressValidator;

public class Mfcoin extends Coin {
    public Mfcoin(){
        super("MFCoin", "MFC", new RegexAddressValidator("^[M][a-km-zA-HJ-NP-Z1-9]{34}$"));
    }
}

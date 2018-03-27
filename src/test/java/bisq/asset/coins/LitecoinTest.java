package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class LitecoinTest extends AbstractAssetTest {

    public LitecoinTest() {
        super(new Litecoin.Mainnet());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("Lg3PX8wRWmApFCoCMAsPF5P9dPHYQHEWKW");
        assertValidAddress("LTuoeY6RBHV3n3cfhXVVTbJbxzxnXs9ofm");
        assertValidAddress("LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW");
        assertInvalidAddress("LgfapHEPhZbdRF9pMd5WPT35hFXcZS1USrW");
        assertInvalidAddress("LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW#");
    }
}

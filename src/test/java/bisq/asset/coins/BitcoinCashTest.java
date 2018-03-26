package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class BitcoinCashTest extends AbstractAssetTest {

    public BitcoinCashTest() {
        super(new BitcoinCash());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH");
        assertValidAddress("1MEbUJ5v5MdDEqFJGz4SZp58KkaLdmXZ85");
        assertValidAddress("34dvotXMg5Gxc37TBVV2e5GUAfCFu7Ms4g");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("21HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHa");
        assertInvalidAddress("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHs");
        assertInvalidAddress("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH#");
    }
}

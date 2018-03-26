package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class BitcoinTest extends AbstractAssetTest {

    public BitcoinTest() {
        super(new Bitcoin.Mainnet());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem");
        assertValidAddress("3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX");
        assertValidAddress("1111111111111111111114oLvT2");
        assertValidAddress("1BitcoinEaterAddressDontSendf59kuE");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#");
    }
}

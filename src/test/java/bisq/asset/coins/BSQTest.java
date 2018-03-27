package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class BSQTest extends AbstractAssetTest {

    public BSQTest() {
        super(new BSQ.Mainnet());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("B17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem");
        assertValidAddress("B3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX");
        assertValidAddress("B1111111111111111111114oLvT2");
        assertValidAddress("B1BitcoinEaterAddressDontSendf59kuE");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("B17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("B17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO");
        assertInvalidAddress("B17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#");
    }
}

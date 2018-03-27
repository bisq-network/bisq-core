package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class PIVXTest extends AbstractAssetTest {

    public PIVXTest() {
        super(new PIVX());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("DFJku78A14HYwPSzC5PtUmda7jMr5pbD2B");
        assertValidAddress("DAeiBSH4nudXgoxS4kY6uhTPobc7ALrWDA");
        assertValidAddress("DRbnCYbuMXdKU4y8dya9EnocL47gFjErWe");
        assertValidAddress("DTPAqTryNRCE2FgsxzohTtJXfCBCDnG6Rc");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#");
    }
}

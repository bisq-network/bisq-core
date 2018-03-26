package bisq.asset.tokens;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class EllaismTest extends AbstractAssetTest {

    public EllaismTest() {
        super(new Ellaism());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0x65767ec6d4d3d18a200842352485cdc37cbf3a21");
        assertValidAddress("65767ec6d4d3d18a200842352485cdc37cbf3a21");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("0x65767ec6d4d3d18a200842352485cdc37cbf3a216");
        assertInvalidAddress("0x65767ec6d4d3d18a200842352485cdc37cbf3a2g");
        assertInvalidAddress("65767ec6d4d3d18a200842352485cdc37cbf3a2g");
    }
}

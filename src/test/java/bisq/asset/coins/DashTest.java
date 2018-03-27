package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class DashTest extends AbstractAssetTest {

    public DashTest() {
        super(new Dash.Mainnet());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("XjNms118hx6dGyBqsrVMTbzMUmxDVijk7Y");
        assertValidAddress("XjNPzWfzGiY1jHUmwn9JDSVMsTs6EtZQMc");
        assertValidAddress("XnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTT");

    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1XnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTT");
        assertInvalidAddress("XnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTTd");
        assertInvalidAddress("XnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTT#");
    }
}

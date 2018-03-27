package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class DogecoinTest extends AbstractAssetTest {

    public DogecoinTest() {
        super(new Dogecoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("DEa7damK8MsbdCJztidBasZKVsDLJifWfE");
        assertValidAddress("DNkkfdUvkCDiywYE98MTVp9nQJTgeZAiFr");
        assertValidAddress("DDWUYQ3GfMDj8hkx8cbnAMYkTzzAunAQxg");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1DDWUYQ3GfMDj8hkx8cbnAMYkTzzAunAQxg");
        assertInvalidAddress("DDWUYQ3GfMDj8hkx8cbnAMYkTzzAunAQxgs");
        assertInvalidAddress("DDWUYQ3GfMDj8hkx8cbnAMYkTzzAunAQxg#");
    }
}

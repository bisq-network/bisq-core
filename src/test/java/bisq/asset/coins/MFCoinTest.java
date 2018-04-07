package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class MFCoinTest extends AbstractAssetTest {
    public MFCoinTest(){
        super(new MFCoin());
    }

    @Test
    public void testValidAddresses(){
        assertValidAddress("Mq7aPKf6xrttnB5so1UVpGMpkmbp7hc47r");
        assertValidAddress("MjQdB9QuDj12Mg5steMNyZzWSTBpSbf7nw");
        assertValidAddress("McFK2Tb4TRqzapbfZnwGGRbjGaRogRS8M6");
    }

    @Test
    public void testInvalidAddresses(){
        assertInvalidAddress("McFK2Tb4TRqzapbfZnwGGRbjGaRogRS8M");
        assertInvalidAddress("McFK2Tb4TRqzapbfZnwGGRbjGaRogRS8Mwqdwqdqwdqwdqwdwd");
        assertInvalidAddress("");
        assertInvalidAddress("McFK2Tb4TRqzapbfZnwGGRbjGaRogRS8MMMMMM");
        assertInvalidAddress("cFK2Tb4TRqzapbfZnwGGRbjGaRogRS8M");
        assertInvalidAddress("cFK2Tb4TRqzapbfZnwGGRbjGaRog8");
        assertInvalidAddress("McFK2Tb4TRqzapbfZnwGGRbjGaRogRS8M6wefweew");
        assertInvalidAddress("cFK2Tb4TRqzapbfZnwGGRbjGaRogRS8M6wefweew");
    }
}

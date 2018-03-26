package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class InstacashTest extends AbstractAssetTest {

    public InstacashTest() {
        super(new Instacash());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("AYx4EqKhomeMu2CTMx1AHdNMkjv6ygnvji");
        assertValidAddress("AcWyvE7texXcCsPLvW1btXhLimrDMpNdAu");
        assertValidAddress("AMfLeLotcvgaHQW374NmHZgs1qXF8P6kjc");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("aYzyJYqhnxF738QjqMqTku5Wft7x4GhVCr");
        assertInvalidAddress("DYzyJYqhnxF738QjqMqTku5Wft7x4GhVCr");
        assertInvalidAddress("xYzyJYqhnxF738QjqMqTku5Wft7x4GhVCr");
        assertInvalidAddress("1YzyJYqhnxF738QjqMqTku5Wft7x4GhVCr");
        assertInvalidAddress(
                "AYzyJYqhnxF738QjqMqTku5Wft7x4GhVCr5vcz2NZLUDsoXGp5rAFUjKnb7DdkFbLp7aSpejCcC4FTxsVvDxq9YKSprzf");
    }
}

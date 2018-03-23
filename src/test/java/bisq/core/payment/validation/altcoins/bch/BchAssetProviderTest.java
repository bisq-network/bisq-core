package bisq.core.payment.validation.altcoins.bch;

import bisq.core.payment.validation.AltCoinAddressValidator;
import bisq.core.payment.validation.AbstractAltcoinAddressValidatorTest;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class BchAssetProviderTest extends AbstractAltcoinAddressValidatorTest {

    @Test
    public void testBCH() {
        AltCoinAddressValidator validator = getAltCoinAddressValidator();
        validator.setCurrencyCode("BCH");

        assertTrue(validator.validate("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH").isValid);
        assertTrue(validator.validate("1MEbUJ5v5MdDEqFJGz4SZp58KkaLdmXZ85").isValid);
        assertTrue(validator.validate("34dvotXMg5Gxc37TBVV2e5GUAfCFu7Ms4g").isValid);

        assertFalse(validator.validate("21HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHa").isValid);
        assertFalse(validator.validate("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHs").isValid);
        assertFalse(validator.validate("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH#").isValid);
        assertFalse(validator.validate("").isValid);
    }

}

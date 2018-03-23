package bisq.core.payment.validation.altcoins.ella;

import bisq.core.payment.validation.altcoins.AbstractAltcoinAddressValidatorTest;
import bisq.core.payment.validation.AltCoinAddressValidator;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class EllaAddressValidatorTest extends AbstractAltcoinAddressValidatorTest {

    @Test
    public void testELLA() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("ELLA");

        assertTrue(validator.validate("0x65767ec6d4d3d18a200842352485cdc37cbf3a21").isValid);
        assertTrue(validator.validate("65767ec6d4d3d18a200842352485cdc37cbf3a21").isValid);

        assertFalse(validator.validate("0x65767ec6d4d3d18a200842352485cdc37cbf3a216").isValid);
        assertFalse(validator.validate("0x65767ec6d4d3d18a200842352485cdc37cbf3a2g").isValid);
        assertFalse(validator.validate("65767ec6d4d3d18a200842352485cdc37cbf3a2g").isValid);
        assertFalse(validator.validate("").isValid);
    }
}

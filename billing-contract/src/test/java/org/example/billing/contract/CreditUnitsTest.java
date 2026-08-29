package org.example.billing.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreditUnitsTest {
    @Test
    void formatsMinorUnitsWithoutConsumerFacingTrailingZeroes() {
        assertEquals("108", CreditUnits.format(10_800, 100));
        assertEquals("108.5", CreditUnits.format(10_850, 100));
        assertEquals("-0.15", CreditUnits.format(-15, 100));
        assertEquals("108", CreditUnits.format(108, 1));
    }

    @Test
    void convertsDisplayedCreditsUsingExactIntegerArithmetic() {
        assertEquals(10_000, CreditUnits.toMinor(100));
        assertThrows(ArithmeticException.class, () -> CreditUnits.toMinor(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> CreditUnits.format(1, 0));
    }
}

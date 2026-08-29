package org.example.billing.contract;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Integer accounting unit and consumer-facing Credit conversion. */
public final class CreditUnits {
    public static final int MINOR_UNITS_PER_CREDIT = 100;

    private CreditUnits() {}

    public static long toMinor(long credits) {
        return Math.multiplyExact(credits, MINOR_UNITS_PER_CREDIT);
    }

    public static BigDecimal toCredits(long minor, int scale) {
        requireSupportedScale(scale);
        return BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(scale), 12, RoundingMode.UNNECESSARY)
                .stripTrailingZeros();
    }

    public static String format(long minor, int scale) {
        return toCredits(minor, scale).toPlainString();
    }

    public static void requireSupportedScale(int scale) {
        int remaining = scale;
        while (remaining > 1 && remaining % 10 == 0) remaining /= 10;
        if (remaining != 1) throw new IllegalArgumentException("credit unit scale must be a positive power of ten");
    }
}

package com.dukaclose.api.common;

import java.math.BigDecimal;
import java.util.Locale;

public final class Money {
    private Money() {}

    /** 1400000 -> "KSh 14,000.00" */
    public static String ksh(long minor) {
        return String.format(Locale.US, "KSh %,.2f", BigDecimal.valueOf(minor, 2));
    }
}
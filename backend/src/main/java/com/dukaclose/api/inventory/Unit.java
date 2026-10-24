package com.dukaclose.api.inventory;

import java.math.BigDecimal;

public enum Unit {
    PIECE(0),   // whole numbers only
    KG(3),      // 0.250 kg is fine
    LITRE(3);

    private final int decimals;

    Unit(int decimals) { this.decimals = decimals; }

    /** True when the quantity has no more decimals than this unit allows. */
    public boolean fitsScale(BigDecimal quantity) {
        return quantity.stripTrailingZeros().scale() <= decimals;
    }
}
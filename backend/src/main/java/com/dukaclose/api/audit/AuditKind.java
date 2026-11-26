// AuditKind.java
package com.dukaclose.api.audit;

import java.util.Arrays;
import java.util.List;

public enum AuditKind {
    SALE_RECORDED(Category.SALES, false), SALE_REVERSED(Category.SALES, false), REFUND_RECORDED(Category.SALES, false),

    DEBT_PAYMENT_RECORDED(Category.DENI, false), DEBT_PAYMENT_REVERSED(Category.DENI, false),
    DEBT_WRITTEN_OFF(Category.DENI, false), CREDIT_LIMIT_CHANGED(Category.DENI, true),

    MONEY_IN_RECORDED(Category.MONEY, false), MONEY_OUT_RECORDED(Category.MONEY, false),
    MONEY_REVERSED(Category.MONEY, false),

    STOCK_OPENING(Category.STOCK, false), STOCK_RECEIVED(Category.STOCK, false), STOCK_ADJUSTED(Category.STOCK, false),

    DAY_OPENED(Category.CLOSE, false), CASH_COUNTED(Category.CLOSE, false), DAY_CLOSED(Category.CLOSE, false),

    ITEM_CREATED(Category.SETUP, true), ITEM_UPDATED(Category.SETUP, true), ITEM_ARCHIVED(Category.SETUP, true),
    ITEM_RESTORED(Category.SETUP, true), PRICE_CHANGED(Category.SETUP, true),
    REORDER_LEVEL_CHANGED(Category.SETUP, true), SHOP_CREATED(Category.SETUP, true),

    CASHIER_CREATED(Category.ACCESS, true), CASHIER_ASSIGNED(Category.ACCESS, true),
    CASHIER_UNASSIGNED(Category.ACCESS, true);

    public enum Category { SALES, DENI, MONEY, STOCK, CLOSE, SETUP, ACCESS }

    private final Category category;
    private final boolean change;

    AuditKind(Category category, boolean change) {
        this.category = category;
        this.change = change;
    }

    public Category category() { return category; }

    /** True for kinds that live in the change log (everything else is a record we already keep). */
    public boolean isChange() { return change; }

    public static List<AuditKind> in(Category category) {
        return Arrays.stream(values()).filter(k -> k.category == category).toList();
    }
}
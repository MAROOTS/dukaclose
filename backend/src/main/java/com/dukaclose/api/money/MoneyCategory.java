package com.dukaclose.api.money;

/** The category decides the accounting meaning. The database repeats these rules as CHECK constraints. */
public enum MoneyCategory {
    STOCK_PURCHASE(MoneyDirection.OUT, false, false),      // becomes stock: not an operating expense
    TRANSPORT(MoneyDirection.OUT, true, false),
    LUNCH(MoneyDirection.OUT, true, false),
    FLOAT_MOVEMENT(MoneyDirection.OUT, false, true),       // cash leaving the drawer: not an expense
    OTHER_EXPENSE(MoneyDirection.OUT, true, false),
    OTHER_CASH_RECEIVED(MoneyDirection.IN, false, true);   // not a sale

    private final MoneyDirection direction;
    private final boolean operatingExpense;
    private final boolean cashOnly;

    MoneyCategory(MoneyDirection direction, boolean operatingExpense, boolean cashOnly) {
        this.direction = direction;
        this.operatingExpense = operatingExpense;
        this.cashOnly = cashOnly;
    }

    public MoneyDirection direction() { return direction; }
    public boolean operatingExpense() { return operatingExpense; }
    public boolean cashOnly() { return cashOnly; }
}
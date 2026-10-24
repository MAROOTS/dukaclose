package com.dukaclose.api.inventory;

import java.util.List;

public record ImportReport(
        boolean committed,
        int rowsRead,
        int newItems,
        int existingItems,
        int openingStockRows,
        long openingValueMinor,
        int errorCount,
        List<Message> errors,
        List<Message> warnings) {

    public record Message(int line, String message) {}
}
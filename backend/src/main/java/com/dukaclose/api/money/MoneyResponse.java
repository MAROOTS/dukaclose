package com.dukaclose.api.money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MoneyResponse(UUID id, UUID shopId, LocalDate businessDate, MoneyDirection direction,
                            MoneyCategory category, MoneyMethod method, long amountMinor, String note,
                            UUID createdById, String createdByName, Instant createdAt,
                            boolean operatingExpense, Reversal reversal) {

    /** Null unless the entry was reversed. The original stays exactly as it was recorded. */
    public record Reversal(String reason, String reversedByName, Instant reversedAt) {}

    public record Page(LocalDate date, List<MoneyResponse> items, int page, int size,
                       long totalElements, int totalPages) {}
}
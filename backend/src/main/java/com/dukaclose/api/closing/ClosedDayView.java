// ClosedDayView.java
package com.dukaclose.api.closing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ClosedDayView(UUID id, UUID shopId, String shopName, LocalDate businessDate, Instant closedAt,
                            String closedByName, long countedMinor, long expectedMinor, long differenceMinor,
                            String note, long salesTotalMinor, ClosePreview snapshot) {

    public record Summary(UUID id, LocalDate businessDate, Instant closedAt, String closedByName,
                          long countedMinor, long expectedMinor, long differenceMinor, long salesTotalMinor,
                          String note) {}

    public record Page(List<Summary> items, int page, int size, long totalElements, int totalPages) {}
}
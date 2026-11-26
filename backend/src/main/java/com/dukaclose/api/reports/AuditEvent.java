package com.dukaclose.api.reports;

import com.dukaclose.api.audit.AuditKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** entityType and entityId say what the event is about, and shopId which shop to open it through. */
public record AuditEvent(Instant occurredAt, UUID shopId, String shopName, AuditKind kind, AuditKind.Category category,
                         UUID actorId, String actorName, String entityType, UUID entityId, Long receiptNo,
                         Long amountMinor, String reason, Map<String, Object> details) {

    public record Page(List<AuditEvent> items, int page, int size, long totalElements, int totalPages,
                       LocalDate from, LocalDate to) {}
}
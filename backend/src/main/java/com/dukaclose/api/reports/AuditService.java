// AuditService.java
package com.dukaclose.api.reports;

import com.dukaclose.api.audit.AuditKind;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.JsonText;
import com.dukaclose.api.day.BusinessClock;
import com.dukaclose.api.reports.ReportScope.Paging;
import com.dukaclose.api.reports.ReportScope.Scope;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final ReportScope scope;
    private final BusinessClock clock;
    private final JdbcTemplate jdbc;
    private final JsonText json;

    @Transactional(readOnly = true)
    public AuditEvent.Page list(UUID userId, UUID businessId, UUID shopId, LocalDate from, LocalDate to,
                                String category, String kind, UUID actorId, int page, int size) {
        if (from == null && to == null) {                      // with no dates, the last seven days
            to = clock.today();
            from = to.minusDays(6);
        }
        Scope s = scope.resolve(userId, businessId, shopId, from, to);
        Paging p = Paging.of(page, size);

        Set<AuditKind> allowed = EnumSet.allOf(AuditKind.class);
        if (category != null && !category.isBlank()) {
            try {
                allowed.retainAll(AuditKind.in(AuditKind.Category.valueOf(category.trim().toUpperCase(Locale.ROOT))));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("INVALID_CATEGORY", "That category does not exist.");
            }
        }
        if (kind != null && !kind.isBlank()) {
            Set<AuditKind> wanted = EnumSet.noneOf(AuditKind.class);
            for (String part : kind.split(",")) {
                try {
                    wanted.add(AuditKind.valueOf(part.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw ApiException.badRequest("INVALID_KIND", "\"" + part.trim() + "\" is not a kind of event.");
                }
            }
            allowed.retainAll(wanted);
        }
        if (allowed.isEmpty()) {
            return new AuditEvent.Page(List.of(), p.page(), p.size(), 0, 0, s.from(), s.to());
        }

        // Dates are Nairobi days, so the cut-offs are Nairobi midnights
        OffsetDateTime start = s.from().atStartOfDay(BusinessClock.ZONE).toOffsetDateTime();
        OffsetDateTime end = s.to().plusDays(1).atStartOfDay(BusinessClock.ZONE).toOffsetDateTime();

        StringBuilder where = new StringBuilder(" FROM audit_events a LEFT JOIN users u ON u.id = a.actor_id"
                + " LEFT JOIN shops sh ON sh.id = a.shop_id WHERE a.business_id = ? AND a.occurred_at >= ?"
                + " AND a.occurred_at < ?");
        List<Object> args = new ArrayList<>(List.of(businessId, start, end));
        if (shopId != null) {
            where.append(" AND a.shop_id = ?");
            args.add(shopId);
        }
        if (allowed.size() < AuditKind.values().length) {
            where.append(" AND a.kind IN (").append(String.join(",", Collections.nCopies(allowed.size(), "?"))).append(")");
            allowed.forEach(k -> args.add(k.name()));
        }
        if (actorId != null) {
            where.append(" AND a.actor_id = ?");
            args.add(actorId);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add(p.size());
        rowArgs.add(p.offset());
        List<AuditEvent> rows = jdbc.query("""
                SELECT a.occurred_at, a.shop_id, sh.name AS shop_name, a.kind, a.actor_id, u.display_name AS actor_name,
                       a.entity_type, a.entity_id, a.receipt_no, a.amount_minor, a.reason, a.details::text AS details"""
                        + where + " ORDER BY a.occurred_at DESC, a.entity_id DESC, a.kind, a.shop_id LIMIT ? OFFSET ?",
                (rs, i) -> {
                    AuditKind k = AuditKind.valueOf(rs.getString("kind"));
                    long receipt = rs.getLong("receipt_no");
                    Long receiptNo = rs.wasNull() ? null : receipt;
                    long amount = rs.getLong("amount_minor");
                    Long amountMinor = rs.wasNull() ? null : amount;
                    return new AuditEvent(rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                            rs.getObject("shop_id", UUID.class), rs.getString("shop_name"), k, k.category(),
                            rs.getObject("actor_id", UUID.class), rs.getString("actor_name"),
                            rs.getString("entity_type"), rs.getObject("entity_id", UUID.class), receiptNo, amountMinor,
                            rs.getString("reason"), json.readMap(rs.getString("details")));
                }, rowArgs.toArray());

        long count = total == null ? 0 : total;
        return new AuditEvent.Page(rows, p.page(), p.size(), count, (int) ((count + p.size() - 1) / p.size()),
                s.from(), s.to());
    }
}
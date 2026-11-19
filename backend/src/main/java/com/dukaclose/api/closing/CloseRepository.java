package com.dukaclose.api.closing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Plain SQL on purpose: the snapshot is stored as JSONB text and never needs to be mapped to an entity. */
@Repository
@RequiredArgsConstructor
class CloseRepository {

    record Row(UUID id, UUID businessId, UUID shopId, UUID shopDayId, LocalDate businessDate, UUID closedBy,
               long countedMinor, long expectedMinor, long differenceMinor, long salesTotalMinor, String note,
               String requestHash, String snapshot, Instant closedAt) {}

    record Summary(UUID id, LocalDate businessDate, UUID closedBy, long countedMinor, long expectedMinor,
                   long differenceMinor, long salesTotalMinor, String note, Instant closedAt) {}

    private static final String ROW_COLUMNS = """
            id, business_id, shop_id, shop_day_id, business_date, closed_by, counted_minor, expected_minor,
            difference_minor, sales_total_minor, note, request_hash, snapshot::text AS snapshot, closed_at""";

    private static final RowMapper<Row> ROW = (rs, i) -> new Row(
            rs.getObject("id", UUID.class), rs.getObject("business_id", UUID.class),
            rs.getObject("shop_id", UUID.class), rs.getObject("shop_day_id", UUID.class),
            rs.getObject("business_date", LocalDate.class), rs.getObject("closed_by", UUID.class),
            rs.getLong("counted_minor"), rs.getLong("expected_minor"), rs.getLong("difference_minor"),
            rs.getLong("sales_total_minor"), rs.getString("note"), rs.getString("request_hash"),
            rs.getString("snapshot"), rs.getObject("closed_at", OffsetDateTime.class).toInstant());

    private static final RowMapper<Summary> SUMMARY = (rs, i) -> new Summary(
            rs.getObject("id", UUID.class), rs.getObject("business_date", LocalDate.class),
            rs.getObject("closed_by", UUID.class), rs.getLong("counted_minor"), rs.getLong("expected_minor"),
            rs.getLong("difference_minor"), rs.getLong("sales_total_minor"), rs.getString("note"),
            rs.getObject("closed_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbc;

    void insert(UUID id, UUID businessId, UUID shopId, UUID dayId, LocalDate date, UUID countId, UUID closedBy,
                long counted, long expected, long difference, long salesTotal, String note, String token,
                String snapshotJson, UUID key, String hash) {
        jdbc.update("""
                insert into day_closes (id, business_id, shop_id, shop_day_id, business_date, cash_count_id,
                    closed_by, counted_minor, expected_minor, difference_minor, sales_total_minor, note,
                    activity_token, snapshot, idempotency_key, request_hash)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)""",
                id, businessId, shopId, dayId, date, countId, closedBy, counted, expected, difference,
                salesTotal, note, token, snapshotJson, key, hash);
    }

    Optional<Row> findByKey(UUID shopId, UUID key) {
        return jdbc.query("select " + ROW_COLUMNS + " from day_closes where shop_id = ? and idempotency_key = ?",
                ROW, shopId, key).stream().findFirst();
    }

    Optional<Row> findByDate(UUID shopId, LocalDate date) {
        return jdbc.query("select " + ROW_COLUMNS + " from day_closes where shop_id = ? and business_date = ?",
                ROW, shopId, date).stream().findFirst();
    }

    List<Summary> summaries(UUID shopId, int limit, long offset) {
        return jdbc.query("""
                select id, business_date, closed_by, counted_minor, expected_minor, difference_minor,
                       sales_total_minor, note, closed_at
                  from day_closes where shop_id = ? order by business_date desc limit ? offset ?""",
                SUMMARY, shopId, limit, offset);
    }

    long countFor(UUID shopId) {
        Long total = jdbc.queryForObject("select count(*) from day_closes where shop_id = ?", Long.class, shopId);
        return total == null ? 0 : total;
    }
}
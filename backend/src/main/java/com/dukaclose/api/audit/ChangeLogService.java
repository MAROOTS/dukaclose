package com.dukaclose.api.audit;

import com.dukaclose.api.common.JsonText;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeLogService {

    private final JdbcTemplate jdbc;
    private final JsonText json;

    /** Builds a before/after map from key, value pairs. Values may be null. Never put secrets or phone numbers in one. */
    public static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    /**
     * Written in the caller's own transaction (MANDATORY refuses to run without one), so a change and its
     * log entry are saved together or not at all.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID businessId, UUID shopId, AuditKind kind, String entityType, UUID entityId, UUID actorId,
                       Map<String, Object> before, Map<String, Object> after) {
        if (!kind.isChange()) {
            throw new IllegalArgumentException(kind + " is not a change-log kind");
        }
        jdbc.update("insert into change_events (business_id, shop_id, kind, entity_type, entity_id, actor_id, "
                        + "before_state, after_state) values (?,?,?,?,?,?,?::jsonb,?::jsonb)",
                businessId, shopId, kind.name(), entityType, entityId, actorId,
                before == null ? null : json.write(before), after == null ? null : json.write(after));
    }
}
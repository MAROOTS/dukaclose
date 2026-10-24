// MovementResponse.java
package com.dukaclose.api.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MovementResponse(UUID id, UUID itemId, MovementType type, BigDecimal quantityDelta,
                               BigDecimal balanceAfter, BigDecimal unitCostMinor, Long totalCostMinor,
                               AdjustmentReason reason, String note, String createdByName, Instant createdAt) {
    static MovementResponse of(StockMovement m, String createdByName) {
        return new MovementResponse(m.getId(), m.getItemId(), m.getMovementType(), m.getQuantityDelta(),
                m.getBalanceAfter(), m.getUnitCost(), m.getTotalCostMinor(), m.getReason(), m.getNote(),
                createdByName, m.getCreatedAt());
    }
}
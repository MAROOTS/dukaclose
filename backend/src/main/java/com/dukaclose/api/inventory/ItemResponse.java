package com.dukaclose.api.inventory;

import com.dukaclose.api.common.RecordStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record ItemResponse(UUID id, UUID businessId, String name, Unit unit,
                           String packName, BigDecimal packSize, boolean active) {
    static ItemResponse of(Item item) {
        return new ItemResponse(item.getId(), item.getBusinessId(), item.getName(), item.getUnit(),
                item.getPackName(), item.getPackSize(), item.getStatus() == RecordStatus.ACTIVE);
    }
}
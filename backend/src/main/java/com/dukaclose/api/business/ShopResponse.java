package com.dukaclose.api.business;

import com.dukaclose.api.common.RecordStatus;

import java.util.UUID;

public record ShopResponse(UUID id, UUID businessId, String name, RecordStatus status, Role myRole) {
    public static ShopResponse of(Shop shop, Role role) {
        return new ShopResponse(shop.getId(), shop.getBusinessId(), shop.getName(), shop.getStatus(), role);
    }
}

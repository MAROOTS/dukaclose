// auth/MeResponse.java
package com.dukaclose.api.auth;

import com.dukaclose.api.business.Role;
import java.util.List;
import java.util.UUID;

public record MeResponse(UserInfo user, List<BusinessInfo> businesses, List<ShopAccess> shops) {
    public record UserInfo(UUID id, String displayName, String loginIdentifier) {}
    public record BusinessInfo(UUID id, String name, Role role) {}
    public record ShopAccess(UUID id, UUID businessId, String name, Role role) {}
}
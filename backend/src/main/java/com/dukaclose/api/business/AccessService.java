package com.dukaclose.api.business;

import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.RecordStatus;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessService {

    private final BusinessRepository businesses;
    private final ShopRepository shops;
    private final BusinessMembershipRepository businessMemberships;
    private final ShopMembershipRepository shopMemberships;

    /** A shop plus the role the current user has on it. */
    public record ShopContext(Shop shop, Role role) {}

    /** Throws 403 unless the user may use this shop (as owner of its business, or as an assigned cashier). */
    @Transactional(readOnly = true)
    public ShopContext requireShopAccess(UUID userId, UUID shopId) {
        Shop shop = shops.findById(shopId)
                .filter(s -> s.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(AccessService::denied);
        businesses.findById(shop.getBusinessId())
                .filter(b -> b.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(AccessService::denied);

        if (isOwner(userId, shop.getBusinessId())) {
            return new ShopContext(shop, Role.OWNER);
        }
        if (shopMemberships.existsByShopIdAndUserIdAndStatus(shopId, userId, RecordStatus.ACTIVE)) {
            return new ShopContext(shop, Role.CASHIER);
        }
        throw denied();
    }

    /** Same, but only owners pass. */
    @Transactional(readOnly = true)
    public ShopContext requireShopOwner(UUID userId, UUID shopId) {
        ShopContext ctx = requireShopAccess(userId, shopId);
        if (ctx.role() != Role.OWNER) {
            throw denied();
        }
        return ctx;
    }

    /** Throws 403 unless the user owns this (active) business. */
    @Transactional(readOnly = true)
    public Business requireBusinessOwner(UUID userId, UUID businessId) {
        Business business = businesses.findById(businessId)
                .filter(b -> b.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(AccessService::denied);
        if (!isOwner(userId, businessId)) {
            throw denied();
        }
        return business;
    }

    @Transactional(readOnly = true)
    public List<Business> ownedBusinesses(UUID userId) {
        List<UUID> ids = businessMemberships.findByUserIdAndStatus(userId, RecordStatus.ACTIVE).stream()
                .filter(m -> m.getRole() == Role.OWNER)
                .map(BusinessMembership::getBusinessId)
                .toList();
        return businesses.findAllById(ids).stream()
                .filter(b -> b.getStatus() == RecordStatus.ACTIVE)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShopContext> accessibleShops(UUID userId) {
        Map<UUID, ShopContext> result = new LinkedHashMap<>();

        // Owner access: every active shop of every business they own
        List<UUID> ownedIds = ownedBusinesses(userId).stream().map(Business::getId).toList();
        if (!ownedIds.isEmpty()) {
            shops.findByBusinessIdIn(ownedIds).stream()
                    .filter(s -> s.getStatus() == RecordStatus.ACTIVE)
                    .forEach(s -> result.put(s.getId(), new ShopContext(s, Role.OWNER)));
        }

        // Cashier access: only explicitly assigned shops (owner role wins if both apply)
        List<UUID> assignedIds = shopMemberships.findByUserIdAndStatus(userId, RecordStatus.ACTIVE).stream()
                .map(ShopMembership::getShopId)
                .toList();
        List<Shop> assigned = shops.findAllById(assignedIds).stream()
                .filter(s -> s.getStatus() == RecordStatus.ACTIVE)
                .toList();
        Set<UUID> activeBusinessIds = businesses
                .findAllById(assigned.stream().map(Shop::getBusinessId).collect(Collectors.toSet())).stream()
                .filter(b -> b.getStatus() == RecordStatus.ACTIVE)
                .map(Business::getId)
                .collect(Collectors.toSet());
        assigned.stream()
                .filter(s -> activeBusinessIds.contains(s.getBusinessId()))
                .forEach(s -> result.putIfAbsent(s.getId(), new ShopContext(s, Role.CASHIER)));

        return result.values().stream()
                .sorted(Comparator.comparing((ShopContext c) -> c.shop().getName()))
                .toList();
    }

    private boolean isOwner(UUID userId, UUID businessId) {
        return businessMemberships.existsByBusinessIdAndUserIdAndRoleAndStatus(
                businessId, userId, Role.OWNER, RecordStatus.ACTIVE);
    }

    // Same answer for "doesn't exist" and "not yours": no information leaks
    private static ApiException denied() {
        return ApiException.forbidden("FORBIDDEN", "You do not have access to this resource");
    }
}
// auth/MeService.java
package com.dukaclose.api.auth;

import com.dukaclose.api.business.*;
import com.dukaclose.api.common.RecordStatus;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeService {

    private final BusinessRepository businesses;
    private final ShopRepository shops;
    private final BusinessMembershipRepository businessMemberships;
    private final ShopMembershipRepository shopMemberships;

    @Transactional(readOnly = true)
    public MeResponse me(AppUserDetails principal) {
        UUID userId = principal.userId();

        // Owners: every shop in every business they own
        List<BusinessMembership> ownerships =
                businessMemberships.findByUserIdAndStatus(userId, RecordStatus.ACTIVE);
        List<UUID> ownedBusinessIds = ownerships.stream().map(BusinessMembership::getBusinessId).toList();

        Map<UUID, MeResponse.ShopAccess> access = new LinkedHashMap<>();
        if (!ownedBusinessIds.isEmpty()) {
            shops.findByBusinessIdIn(ownedBusinessIds).stream()
                    .filter(s -> s.getStatus() == RecordStatus.ACTIVE)
                    .forEach(s -> access.put(s.getId(), toAccess(s, Role.OWNER)));
        }

        // Cashiers: only shops they are explicitly assigned to (owner role wins if both apply)
        List<UUID> assignedShopIds = shopMemberships.findByUserIdAndStatus(userId, RecordStatus.ACTIVE)
                .stream().map(ShopMembership::getShopId).toList();
        shops.findAllById(assignedShopIds).stream()
                .filter(s -> s.getStatus() == RecordStatus.ACTIVE)
                .forEach(s -> access.putIfAbsent(s.getId(), toAccess(s, Role.CASHIER)));

        // Drop anything that belongs to an inactive business
        Set<UUID> businessIds = new HashSet<>(ownedBusinessIds);
        access.values().forEach(a -> businessIds.add(a.businessId()));
        Map<UUID, Business> activeBusinesses = businesses.findAllById(businessIds).stream()
                .filter(b -> b.getStatus() == RecordStatus.ACTIVE)
                .collect(Collectors.toMap(Business::getId, Function.identity()));

        List<MeResponse.BusinessInfo> businessInfos = ownerships.stream()
                .filter(m -> activeBusinesses.containsKey(m.getBusinessId()))
                .map(m -> new MeResponse.BusinessInfo(
                        m.getBusinessId(), activeBusinesses.get(m.getBusinessId()).getName(), m.getRole()))
                .toList();

        List<MeResponse.ShopAccess> shopInfos = access.values().stream()
                .filter(a -> activeBusinesses.containsKey(a.businessId()))
                .toList();

        return new MeResponse(
                new MeResponse.UserInfo(userId, principal.displayName(), principal.loginIdentifier()),
                businessInfos,
                shopInfos);
    }

    private MeResponse.ShopAccess toAccess(Shop shop, Role role) {
        return new MeResponse.ShopAccess(shop.getId(), shop.getBusinessId(), shop.getName(), role);
    }
}
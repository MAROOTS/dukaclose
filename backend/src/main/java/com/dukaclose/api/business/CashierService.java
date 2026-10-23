// CashierService.java
package com.dukaclose.api.business;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.PhoneNumbers;
import com.dukaclose.api.common.RecordStatus;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashierService {

    private final AccessService access;
    private final ShopRepository shops;
    private final UserRepository users;
    private final ShopMembershipRepository shopMemberships;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CashierResponse create(UUID ownerId, UUID businessId, CreateCashierRequest request) {
        access.requireBusinessOwner(ownerId, businessId);

        String phone = PhoneNumbers.normalise(request.phone());

        Set<UUID> wanted = new HashSet<>(request.shopIds());
        List<Shop> targets = shops.findAllById(wanted);
        boolean valid = targets.size() == wanted.size() && targets.stream().allMatch(
                s -> s.getBusinessId().equals(businessId) && s.getStatus() == RecordStatus.ACTIVE);
        if (!valid) {
            throw ApiException.badRequest("INVALID_SHOPS", "One or more shops do not belong to this business");
        }

        if (users.existsByLoginIdentifier(phone)) {
            throw ApiException.conflict("PHONE_TAKEN", "That phone number is already registered");
        }

        User user = users.save(new User(
                request.displayName().trim(), phone, passwordEncoder.encode(request.password())));
        targets.forEach(s -> shopMemberships.save(new ShopMembership(s.getId(), user.getId(), Role.CASHIER)));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<CashierResponse> listForShop(UUID ownerId, UUID shopId) {
        access.requireShopOwner(ownerId, shopId);
        List<UUID> userIds = shopMemberships.findByShopIdAndStatus(shopId, RecordStatus.ACTIVE).stream()
                .map(ShopMembership::getUserId)
                .toList();
        return users.findAllById(userIds).stream()
                .sorted(Comparator.comparing(User::getDisplayName))
                .map(this::toResponse)
                .toList();
    }

    /** Give an existing cashier of this business access to another shop (or restore removed access). */
    @Transactional
    public void assign(UUID ownerId, UUID shopId, UUID cashierId) {
        Shop shop = access.requireShopOwner(ownerId, shopId).shop();

        List<UUID> businessShopIds = shops.findByBusinessId(shop.getBusinessId()).stream()
                .map(Shop::getId)
                .toList();
        if (!shopMemberships.existsByShopIdInAndUserId(businessShopIds, cashierId)) {
            throw ApiException.notFound("CASHIER_NOT_FOUND", "No such cashier in this business");
        }

        shopMemberships.findByShopIdAndUserId(shopId, cashierId).ifPresentOrElse(
                ShopMembership::activate,
                () -> shopMemberships.save(new ShopMembership(shopId, cashierId, Role.CASHIER)));
    }

    @Transactional
    public void unassign(UUID ownerId, UUID shopId, UUID cashierId) {
        access.requireShopOwner(ownerId, shopId);
        ShopMembership membership = shopMemberships.findByShopIdAndUserId(shopId, cashierId)
                .orElseThrow(() -> ApiException.notFound("CASHIER_NOT_FOUND", "No such cashier on this shop"));
        membership.deactivate();
    }

    private CashierResponse toResponse(User user) {
        return new CashierResponse(user.getId(), user.getDisplayName(), user.getLoginIdentifier());
    }
}
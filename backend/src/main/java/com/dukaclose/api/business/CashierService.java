// CashierService.java
package com.dukaclose.api.business;

import com.dukaclose.api.audit.AuditKind;
import com.dukaclose.api.audit.ChangeLogService;
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
    private final ChangeLogService changeLog;
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

        List<String> shopNames = targets.stream().map(Shop::getName).sorted().toList();
        changeLog.record(businessId, null, AuditKind.CASHIER_CREATED, "USER", user.getId(), ownerId, null,
                ChangeLogService.map("displayName", user.getDisplayName(), "shops", shopNames));
        targets.forEach(s -> changeLog.record(businessId, s.getId(), AuditKind.CASHIER_ASSIGNED, "USER", user.getId(),
                ownerId, null, ChangeLogService.map("cashier", user.getDisplayName(), "shop", s.getName())));
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

        List<UUID> businessShopIds = shops.findByBusinessId(shop.getBusinessId()).stream().map(Shop::getId).toList();
        if (!shopMemberships.existsByShopIdInAndUserId(businessShopIds, cashierId)) {
            throw ApiException.notFound("CASHIER_NOT_FOUND", "No such cashier in this business");
        }
        String cashierName = users.findById(cashierId).map(User::getDisplayName).orElse("");

        Optional<ShopMembership> existing = shopMemberships.findByShopIdAndUserId(shopId, cashierId);
        if (existing.isPresent()) {
            ShopMembership membership = existing.get();
            if (membership.getStatus() != RecordStatus.ACTIVE) {
                membership.activate();
                logAssignment(AuditKind.CASHIER_ASSIGNED, ownerId, shop, cashierId, cashierName);
            }
        } else {
            shopMemberships.save(new ShopMembership(shopId, cashierId, Role.CASHIER));
            logAssignment(AuditKind.CASHIER_ASSIGNED, ownerId, shop, cashierId, cashierName);
        }
    }

    @Transactional
    public void unassign(UUID ownerId, UUID shopId, UUID cashierId) {
        Shop shop = access.requireShopOwner(ownerId, shopId).shop();
        ShopMembership membership = shopMemberships.findByShopIdAndUserId(shopId, cashierId)
                .orElseThrow(() -> ApiException.notFound("CASHIER_NOT_FOUND", "No such cashier on this shop"));
        if (membership.getStatus() == RecordStatus.ACTIVE) {
            membership.deactivate();
            logAssignment(AuditKind.CASHIER_UNASSIGNED, ownerId, shop, cashierId,
                    users.findById(cashierId).map(User::getDisplayName).orElse(""));
        }
    }

    private void logAssignment(AuditKind kind, UUID ownerId, Shop shop, UUID cashierId, String cashierName) {
        changeLog.record(shop.getBusinessId(), shop.getId(), kind, "USER", cashierId, ownerId, null,
                ChangeLogService.map("cashier", cashierName, "shop", shop.getName()));
    }

    private CashierResponse toResponse(User user) {
        return new CashierResponse(user.getId(), user.getDisplayName(), user.getLoginIdentifier());
    }
}
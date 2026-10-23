// ShopService.java
package com.dukaclose.api.business;

import com.dukaclose.api.common.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final AccessService access;
    private final ShopRepository shops;

    @Transactional
    public ShopResponse create(UUID userId, CreateShopRequest request) {
        Business business = access.requireBusinessOwner(userId, request.businessId());
        String name = request.name().trim();
        if (shops.existsByBusinessIdAndName(business.getId(), name)) {
            throw ApiException.conflict("SHOP_NAME_TAKEN", "You already have a shop with that name");
        }
        Shop shop = shops.save(new Shop(business.getId(), name));
        return ShopResponse.of(shop, Role.OWNER);
    }

    @Transactional(readOnly = true)
    public List<ShopResponse> list(UUID userId) {
        return access.accessibleShops(userId).stream()
                .map(c -> ShopResponse.of(c.shop(), c.role()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShopResponse get(UUID userId, UUID shopId) {
        AccessService.ShopContext ctx = access.requireShopAccess(userId, shopId);
        return ShopResponse.of(ctx.shop(), ctx.role());
    }
}
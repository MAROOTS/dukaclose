package com.dukaclose.api.auth;

import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Role;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeService {

    private final AccessService access;

    @Transactional(readOnly = true)
    public MeResponse me(AppUserDetails principal) {
        UUID userId = principal.userId();

        List<MeResponse.BusinessInfo> businessInfos = access.ownedBusinesses(userId).stream()
                .map(b -> new MeResponse.BusinessInfo(b.getId(), b.getName(), Role.OWNER))
                .toList();

        List<MeResponse.ShopAccess> shopInfos = access.accessibleShops(userId).stream()
                .map(c -> new MeResponse.ShopAccess(
                        c.shop().getId(), c.shop().getBusinessId(), c.shop().getName(), c.role()))
                .toList();

        return new MeResponse(
                new MeResponse.UserInfo(userId, principal.displayName(), principal.loginIdentifier()),
                businessInfos,
                shopInfos);
    }
}
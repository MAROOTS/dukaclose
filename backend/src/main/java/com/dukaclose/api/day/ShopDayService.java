// ShopDayService.java
package com.dukaclose.api.day;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShopDayService {

    private final AccessService access;
    private final ShopDayRepository days;
    private final UserRepository users;
    private final BusinessClock clock;

    @Transactional(readOnly = true)
    public DayStatusResponse status(UUID userId, UUID shopId) {
        access.requireShopAccess(userId, shopId);
        LocalDate today = clock.today();
        ShopDayResponse open = days.findByShopIdAndStatus(shopId, DayStatus.OPEN)
                .map(day -> toResponse(day, today))
                .orElse(null);
        return new DayStatusResponse(today, open);
    }

    @Transactional
    public ShopDayResponse open(UUID userId, UUID shopId, OpenDayRequest request) {
        Shop shop = access.requireShopAccess(userId, shopId).shop();
        LocalDate today = clock.today();

        Optional<ShopDay> open = days.findByShopIdAndStatus(shopId, DayStatus.OPEN);
        if (open.isPresent()) {
            LocalDate date = open.get().getBusinessDate();
            if (date.isBefore(today)) {
                throw ApiException.conflict("PREVIOUS_DAY_OPEN",
                        "The day of " + date + " is still open. Close it before starting a new day.");
            }
            throw ApiException.conflict("DAY_ALREADY_OPEN", "Today is already open.");
        }
        if (days.existsByShopIdAndBusinessDate(shopId, today)) {
            throw ApiException.conflict("DAY_ALREADY_CLOSED",
                    "Today was already closed. A new day can be opened tomorrow.");
        }

        ShopDay day = days.saveAndFlush(
                new ShopDay(shopId, shop.getBusinessId(), today, request.openingFloatMinor(), userId));
        return toResponse(day, today);
    }

    private ShopDayResponse toResponse(ShopDay day, LocalDate today) {
        String name = users.findById(day.getOpenedBy()).map(User::getDisplayName).orElse("");
        return new ShopDayResponse(day.getId(), day.getShopId(), day.getBusinessDate(), day.getStatus(),
                day.getOpeningFloatMinor(), name, day.getCreatedAt(), day.getBusinessDate().isBefore(today));
    }
}
package com.dukaclose.api.day;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Role;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShopDayService {

    private final AccessService access;
    private final ShopDayRepository days;
    private final UserRepository users;
    private final BusinessClock clock;
    private final JdbcTemplate jdbc;

    private record LastClose(LocalDate date, long countedMinor) {}

    @Transactional(readOnly = true)
    public DayStatusResponse status(UUID userId, UUID shopId) {
        AccessService.ShopContext ctx = access.requireShopAccess(userId, shopId);
        boolean owner = ctx.role() == Role.OWNER;
        LocalDate today = clock.today();
        ShopDayResponse open = days.findByShopIdAndStatus(shopId, DayStatus.OPEN)
                .map(day -> toResponse(day, today, owner))
                .orElse(null);
        LastClose last = lastClose(shopId);
        return new DayStatusResponse(today, open,
                last == null ? null : last.countedMinor(), last == null ? null : last.date());
    }

    @Transactional
    public ShopDayResponse open(UUID userId, UUID shopId, OpenDayRequest request) {
        AccessService.ShopContext ctx = access.requireShopAccess(userId, shopId);
        Shop shop = ctx.shop();
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

        LastClose last = lastClose(shopId);
        ShopDay day = days.saveAndFlush(new ShopDay(shopId, shop.getBusinessId(), today,
                request.openingFloatMinor(), userId, last == null ? null : last.countedMinor()));
        return toResponse(day, today, ctx.role() == Role.OWNER);
    }

    /** Dates are sequential for a shop, so the latest date is the latest close. */
    private LastClose lastClose(UUID shopId) {
        List<LastClose> rows = jdbc.query(
                "select business_date, counted_minor from day_closes where shop_id = ? "
                        + "order by business_date desc limit 1",
                (rs, i) -> new LastClose(rs.getObject("business_date", LocalDate.class), rs.getLong("counted_minor")),
                shopId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The float difference is for the owner's eyes. A cashier only gets the suggested amount. */
    private ShopDayResponse toResponse(ShopDay day, LocalDate today, boolean owner) {
        String name = users.findById(day.getOpenedBy()).map(User::getDisplayName).orElse("");
        Long carried = owner ? day.getCarriedOverMinor() : null;
        Long difference = carried == null ? null : day.getOpeningFloatMinor() - carried;
        return new ShopDayResponse(day.getId(), day.getShopId(), day.getBusinessDate(), day.getStatus(),
                day.getOpeningFloatMinor(), name, day.getCreatedAt(), day.getBusinessDate().isBefore(today),
                carried, difference);
    }
}
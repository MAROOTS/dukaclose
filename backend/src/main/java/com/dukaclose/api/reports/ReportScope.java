// ReportScope.java
package com.dukaclose.api.reports;

import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.business.ShopRepository;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.day.BusinessClock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Who may look, at which shops, and over which dates. Every owner report starts here. */
@Component
@RequiredArgsConstructor
class ReportScope {

    static final int MAX_RANGE_DAYS = 366;

    record Scope(UUID businessId, List<UUID> shopIds, LocalDate from, LocalDate to) {
        /** The placeholders for "shop_id IN (...)". An empty business matches nothing. */
        String in() {
            return shopIds.isEmpty() ? "NULL" : String.join(",", Collections.<String>nCopies(shopIds.size(), "?"));
        }

        /** The shop ids, then from and to: the arguments of "shop IN (...) AND date BETWEEN ? AND ?". */
        List<Object> args() {
            List<Object> list = new ArrayList<>(shopIds);
            list.add(from);
            list.add(to);
            return list;
        }
    }

    record Paging(int page, int size) {
        static Paging of(int page, int size) {
            return new Paging(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        }
        long offset() { return (long) page * size; }
    }

    private final AccessService access;
    private final ShopRepository shops;
    private final BusinessClock clock;

    Scope resolve(UUID userId, UUID businessId, UUID shopId, LocalDate from, LocalDate to) {
        access.requireBusinessOwner(userId, businessId);

        LocalDate today = clock.today();
        LocalDate start = from == null ? today : from;
        LocalDate end = to == null ? today : to;
        if (end.isAfter(today)) {
            throw ApiException.badRequest("INVALID_DATE", "That date is in the future.");
        }
        if (start.isAfter(end)) {
            throw ApiException.badRequest("INVALID_RANGE", "The start date is after the end date.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_LARGE", "Pick a range of at most " + MAX_RANGE_DAYS + " days.");
        }

        List<UUID> ids = shops.findByBusinessId(businessId).stream().map(Shop::getId).toList();
        if (shopId != null) {
            if (!ids.contains(shopId)) {
                throw ApiException.forbidden("FORBIDDEN", "You do not have access to this resource");
            }
            ids = List.of(shopId);
        }
        return new Scope(businessId, ids, start, end);
    }
}
// ClosedDayService.java
package com.dukaclose.api.closing;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Role;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClosedDayService {

    private final AccessService access;
    private final CloseRepository closes;
    private final UserRepository users;
    private final SnapshotJson snapshots;

    @Transactional(readOnly = true)
    public ClosedDayView.Page list(UUID userId, UUID shopId, int page, int size) {
        access.requireShopAccess(userId, shopId);
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 50);

        long total = closes.countFor(shopId);
        List<CloseRepository.Summary> rows = closes.summaries(shopId, s, (long) p * s);
        Map<UUID, String> names = users.findAllById(
                        rows.stream().map(CloseRepository.Summary::closedBy).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        List<ClosedDayView.Summary> items = rows.stream().map(r -> new ClosedDayView.Summary(
                r.id(), r.businessDate(), r.closedAt(), names.getOrDefault(r.closedBy(), ""), r.countedMinor(),
                r.expectedMinor(), r.differenceMinor(), r.salesTotalMinor(), r.note())).toList();
        return new ClosedDayView.Page(items, p, s, total, (int) ((total + s - 1) / s));
    }

    @Transactional(readOnly = true)
    public ClosedDayView get(UUID userId, UUID shopId, LocalDate date) {
        AccessService.ShopContext ctx = access.requireShopAccess(userId, shopId);
        CloseRepository.Row row = closes.findByDate(shopId, date)
                .orElseThrow(() -> ApiException.notFound("CLOSE_NOT_FOUND", "That day has not been closed."));
        return view(row, ctx.shop(), ctx.role() == Role.OWNER);
    }

    /** Cashiers get the snapshot without profit, shrinkage, write-offs or the list of counts. */
    ClosedDayView view(CloseRepository.Row row, Shop shop, boolean owner) {
        ClosePreview snapshot = snapshots.read(row.snapshot(), ClosePreview.class);
        String closedBy = users.findById(row.closedBy()).map(User::getDisplayName).orElse("");
        return new ClosedDayView(row.id(), row.shopId(), shop.getName(), row.businessDate(), row.closedAt(),
                closedBy, row.countedMinor(), row.expectedMinor(), row.differenceMinor(), row.note(),
                row.salesTotalMinor(), owner ? snapshot : snapshot.withoutOwnerFigures());
    }
}
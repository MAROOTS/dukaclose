// CashCountService.java
package com.dukaclose.api.closing;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.cash.DayCashService;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.Hashing;
import com.dukaclose.api.day.DayStatus;
import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashCountService {

    private final AccessService access;
    private final ShopDayRepository days;
    private final CashCountRepository counts;
    private final DayCashService cash;
    private final ClosePreviewService preview;
    private final UserRepository users;

    public record Result(CashCountResponse count, boolean replayed) {}

    @Transactional
    public Result record(UUID userId, UUID shopId, UUID key, CashCountRequest request) {
        Shop shop = access.requireShopAccess(userId, shopId).shop();
        String hash = Hashing.sha256Hex("count:" + request.countedMinor());

        // A retry of a count we already saved gets the original back, even if the day has closed since
        Optional<Result> replay = replayOf(shopId, key, hash);
        if (replay.isPresent()) return replay.get();

        // Shared lock, like every other write: a closing day waits for counts in flight
        ShopDay day = days.findLockedForShare(shopId, DayStatus.OPEN)
                .orElseThrow(() -> ApiException.conflict("NO_OPEN_DAY", "There is no open day to count cash for."));

        // Expected cash and the activity token are both taken now, from the same saved records
        long expected = cash.summary(day.getId()).expectedCashMinor();
        String token = preview.activityToken(day.getId());

        CashCount saved = counts.saveAndFlush(new CashCount(shop.getBusinessId(), shopId, day.getId(),
                request.countedMinor(), expected, token, userId, key, hash));
        return new Result(toResponse(saved), false);
    }

    private Optional<Result> replayOf(UUID shopId, UUID key, String hash) {
        return counts.findByShopIdAndIdempotencyKey(shopId, key).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw ApiException.conflict("IDEMPOTENCY_CONFLICT",
                        "That Idempotency-Key was already used for a different count.");
            }
            return new Result(toResponse(existing), true);
        });
    }

    private CashCountResponse toResponse(CashCount c) {
        int number = (int) counts.countByShopDayIdAndCreatedAtLessThanEqual(c.getShopDayId(), c.getCreatedAt());
        String name = users.findById(c.getCountedBy()).map(User::getDisplayName).orElse("");
        return new CashCountResponse(c.getId(), number, c.getCountedMinor(), c.getExpectedMinor(),
                c.getCountedMinor() - c.getExpectedMinor(), c.getActivityToken(), name, c.getCreatedAt());
    }
}
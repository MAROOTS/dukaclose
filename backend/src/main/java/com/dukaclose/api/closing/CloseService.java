package com.dukaclose.api.closing;

import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Role;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.cash.DayCashService;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.Hashing;
import com.dukaclose.api.common.Money;
import com.dukaclose.api.day.DayStatus;
import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CloseService {

    private final AccessService access;
    private final ShopDayRepository days;
    private final CashCountRepository counts;
    private final DayCashService cash;
    private final ClosePreviewService preview;
    private final CloseRepository closes;
    private final ClosedDayService views;
    private final SnapshotJson snapshots;

    public record Result(ClosedDayView close, boolean replayed) {}

    @Transactional
    public Result close(UUID userId, UUID shopId, UUID key, CloseRequest request) {
        AccessService.ShopContext ctx = access.requireShopAccess(userId, shopId);
        Shop shop = ctx.shop();
        boolean owner = ctx.role() == Role.OWNER;
        String note = request.note() == null ? "" : request.note().trim();
        String hash = Hashing.sha256Hex("close:" + request.cashCountId() + ":" + note);

        // A retry of a close we already saved gets the original back, even though the day is closed now
        Optional<Result> replay = replayOf(shop, key, hash, owner);
        if (replay.isPresent()) return replay.get();

        // The exclusive lock: waits for every sale, repayment, money entry, count and stock movement in flight,
        // and makes anything arriving afterwards find no open day
        ShopDay day = days.findLockedForUpdate(shopId, DayStatus.OPEN).orElse(null);
        if (day == null) {
            replay = replayOf(shop, key, hash, owner);       // an identical request may have closed it while we waited
            if (replay.isPresent()) return replay.get();
            throw ApiException.conflict("NO_OPEN_DAY", "There is no open day to close.");
        }

        CashCount count = counts.findById(request.cashCountId())
                .filter(c -> c.getShopDayId().equals(day.getId()))
                .orElseThrow(() -> ApiException.notFound("COUNT_NOT_FOUND", "That cash count was not found for today."));

        List<CashCount> all = counts.findByShopDayIdOrderByCreatedAtAsc(day.getId());
        if (!all.get(all.size() - 1).getId().equals(count.getId())) {
            throw ApiException.conflict("COUNT_SUPERSEDED",
                    "The drawer was counted again since. Close the day using the latest count.");
        }

        // Rechecked now, under the lock, from the saved records: the browser's numbers are never trusted
        if (!count.getActivityToken().equals(preview.activityToken(day.getId()))) {
            throw ApiException.conflict("COUNT_STALE",
                    "Sales or money were recorded after you counted. Count the drawer again.");
        }
        long expected = cash.summary(day.getId()).expectedCashMinor();
        long counted = count.getCountedMinor();
        long difference = counted - expected;
        if (difference != counted - count.getExpectedMinor()) {
            throw ApiException.conflict("COUNT_STALE", "The expected cash changed. Count the drawer again.");
        }

        if (difference != 0 && note.length() < 3) {
            throw ApiException.badRequest("NOTE_REQUIRED", "The drawer is " + (difference < 0 ? "short" : "over")
                    + " by " + Money.ksh(Math.abs(difference)) + ". Add a note explaining why.");
        }

        // Everything above is checked. Now freeze the day: snapshot, close record, status. All or nothing.
        ClosePreview snapshot = preview.build(shop, day, true);
        UUID closeId = UUID.randomUUID();
        closes.insert(closeId, shop.getBusinessId(), shopId, day.getId(), day.getBusinessDate(), count.getId(),
                userId, counted, expected, difference, snapshot.sales().totalMinor(),
                note.isEmpty() ? null : note, count.getActivityToken(), snapshots.write(snapshot), key, hash);
        day.markClosed();
        days.saveAndFlush(day);

        CloseRepository.Row row = closes.findByKey(shopId, key).orElseThrow();
        return new Result(views.view(row, shop, owner), false);
    }

    private Optional<Result> replayOf(Shop shop, UUID key, String hash, boolean owner) {
        return closes.findByKey(shop.getId(), key).map(existing -> {
            if (!existing.requestHash().equals(hash)) {
                throw ApiException.conflict("IDEMPOTENCY_CONFLICT",
                        "That Idempotency-Key was already used for a different close.");
            }
            return new Result(views.view(existing, shop, owner), true);
        });
    }
}
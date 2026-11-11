package com.dukaclose.api.money;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Role;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.Hashing;
import com.dukaclose.api.day.BusinessClock;
import com.dukaclose.api.day.DayStatus;
import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MoneyService {

    private final AccessService access;
    private final ShopDayRepository days;
    private final MoneyMovementRepository movements;
    private final MoneyMovementReversalRepository reversals;
    private final UserRepository users;
    private final BusinessClock clock;

    public record Result(MoneyResponse movement, boolean replayed) {}

    @Transactional
    public Result create(UUID userId, UUID shopId, UUID key, MoneyRequest request) {
        Shop shop = access.requireShopAccess(userId, shopId).shop();
        String note = request.note().trim();
        String hash = Hashing.sha256Hex(request.category() + ":" + request.method() + ":"
                + request.amountMinor() + ":" + note);

        // A retry of an entry we already saved gets the original back, even if the day has closed since
        Optional<Result> replay = replayOf(shopId, key, hash);
        if (replay.isPresent()) return replay.get();

        ShopDay day = days.findLockedForShare(shopId, DayStatus.OPEN)
                .orElseThrow(() -> ApiException.conflict("NO_OPEN_DAY", "Open today’s book before recording money."));

        MoneyCategory category = request.category();
        if (category.cashOnly() && request.method() != MoneyMethod.CASH) {
            throw ApiException.badRequest("CASH_ONLY", category.direction() == MoneyDirection.IN
                    ? "Money received into the drawer is always cash."
                    : "A float movement is cash leaving the drawer, so it is always cash.");
        }
        if (note.length() < 3) {
            throw ApiException.badRequest("NOTE_REQUIRED", "Add a short note saying what this was for (at least 3 characters).");
        }

        MoneyMovement saved = movements.saveAndFlush(new MoneyMovement(shop.getBusinessId(), shopId, day.getId(),
                category, request.method(), request.amountMinor(), note, userId, key, hash));
        return new Result(one(saved), false);
    }

    /** Defaults to the shop's current business day. Newest entry first. */
    @Transactional(readOnly = true)
    public MoneyResponse.Page list(UUID userId, UUID shopId, LocalDate date, int page, int size) {
        access.requireShopAccess(userId, shopId);
        LocalDate target = date != null ? date
                : days.findByShopIdAndStatus(shopId, DayStatus.OPEN).map(ShopDay::getBusinessDate).orElse(clock.today());
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50),
                Sort.by(Sort.Direction.DESC, "seq"));

        Page<MoneyMovement> result = days.findByShopIdAndBusinessDate(shopId, target)
                .map(day -> movements.findByShopDayId(day.getId(), pageable))
                .orElse(Page.empty(pageable));
        return new MoneyResponse.Page(target, responses(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public MoneyResponse reverse(UUID userId, UUID shopId, UUID movementId, String rawReason) {
        AccessService.ShopContext ctx = access.requireShopAccess(userId, shopId);
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.length() < 3) {
            throw ApiException.badRequest("REASON_REQUIRED", "Say why this entry is being reversed (at least 3 characters).");
        }

        // Same order as everywhere else: the day first, then the row that changes
        ShopDay openDay = days.findLockedForShare(shopId, DayStatus.OPEN).orElse(null);
        MoneyMovement movement = movements.findForUpdate(movementId, shopId)
                .orElseThrow(() -> ApiException.notFound("MOVEMENT_NOT_FOUND", "No such entry"));

        if (ctx.role() != Role.OWNER && !movement.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("NOT_YOUR_ENTRY",
                    "You can only reverse entries you recorded yourself. Ask the owner.");
        }
        if (openDay == null || !openDay.getId().equals(movement.getShopDayId())) {
            throw ApiException.conflict("DAY_CLOSED",
                    "This entry belongs to a day that is already closed. Ask the owner to record an adjustment.");
        }
        if (reversals.existsByMovementId(movementId)) {
            throw ApiException.conflict("ALREADY_REVERSED", "This entry was already reversed.");
        }

        reversals.saveAndFlush(new MoneyMovementReversal(movementId, openDay.getId(), shopId, reason, userId));
        return one(movement);
    }

    // ------------------------------------------------------------------ helpers

    private Optional<Result> replayOf(UUID shopId, UUID key, String hash) {
        return movements.findByShopIdAndIdempotencyKey(shopId, key).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw ApiException.conflict("IDEMPOTENCY_CONFLICT",
                        "That Idempotency-Key was already used for a different entry.");
            }
            return new Result(one(existing), true);
        });
    }

    MoneyResponse one(MoneyMovement movement) {
        return responses(List.of(movement)).get(0);
    }

    /** Builds responses for many entries with a handful of queries. */
    List<MoneyResponse> responses(List<MoneyMovement> list) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(MoneyMovement::getId).toList();

        Map<UUID, MoneyMovementReversal> reversalByMovement = reversals.findByMovementIdIn(ids).stream()
                .collect(Collectors.toMap(MoneyMovementReversal::getMovementId, Function.identity()));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(m -> userIds.add(m.getCreatedBy()));
        reversalByMovement.values().forEach(r -> userIds.add(r.getReversedBy()));
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        Map<UUID, LocalDate> dates = days.findAllById(
                        list.stream().map(MoneyMovement::getShopDayId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ShopDay::getId, ShopDay::getBusinessDate));

        return list.stream().map(m -> {
            MoneyMovementReversal r = reversalByMovement.get(m.getId());
            MoneyResponse.Reversal reversal = r == null ? null : new MoneyResponse.Reversal(
                    r.getReason(), names.getOrDefault(r.getReversedBy(), ""), r.getCreatedAt());
            return new MoneyResponse(m.getId(), m.getShopId(), dates.get(m.getShopDayId()), m.getDirection(),
                    m.getCategory(), m.getMethod(), m.getAmountMinor(), m.getNote(), m.getCreatedBy(),
                    names.getOrDefault(m.getCreatedBy(), ""), m.getCreatedAt(),
                    m.getCategory().operatingExpense(), reversal);
        }).toList();
    }
}
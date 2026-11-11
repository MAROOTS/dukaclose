package com.dukaclose.api.deni;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Role;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.Hashing;
import com.dukaclose.api.common.Money;
import com.dukaclose.api.day.BusinessClock;
import com.dukaclose.api.day.DayStatus;
import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import com.dukaclose.api.sales.Sale;
import com.dukaclose.api.sales.SaleRepository;
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
public class DebtPaymentService {

    private final AccessService access;
    private final ShopDayRepository days;
    private final CustomerRepository customers;
    private final CustomerDebtRepository debts;
    private final DebtPaymentRepository payments;
    private final DebtPaymentAllocationRepository allocationRepo;
    private final DebtPaymentReversalRepository reversalRepo;
    private final DebtService debtService;
    private final SaleRepository sales;
    private final UserRepository users;
    private final BusinessClock clock;

    public record Result(PaymentResponse payment, boolean replayed) {}

    // --collecting

    @Transactional
    public Result pay(UUID userId, UUID shopId, UUID key, RepayRequest request) {
        Shop shop = access.requireShopAccess(userId, shopId).shop();
        String hash = Hashing.sha256Hex(request.customerId() + ":" + request.method() + ":"
                + request.amountMinor() + ":" + request.debtId());

        // A retry of a payment we already saved gets the original back, even if the day has closed since
        Optional<Result> replay = replayOf(shopId, key, hash);
        if (replay.isPresent()) return replay.get();

        // Shared lock on the day, like a sale: a closing day waits for payments in flight
        ShopDay day = days.findLockedForShare(shopId, DayStatus.OPEN)
                .orElseThrow(() -> ApiException.conflict("NO_OPEN_DAY", "Open today’s book before collecting deni."));

        Customer customer = customers.findByIdAndBusinessId(request.customerId(), shop.getBusinessId())
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "No such customer"));

        // Lock all of this customer's debts: two payments at once cannot both pass the "owes enough" check
        List<CustomerDebt> active = debts.lockActiveForCustomer(customer.getId());

        replay = replayOf(shopId, key, hash);       // a duplicate that was waiting on the lock
        if (replay.isPresent()) return replay.get();

        Map<UUID, DebtService.Balance> balances = debtService.balances(active);
        long amount = request.amountMinor();

        List<CustomerDebt> targets;
        if (request.debtId() != null) {
            CustomerDebt chosen = active.stream().filter(d -> d.getId().equals(request.debtId())).findFirst()
                    .orElseThrow(() -> ApiException.notFound("DEBT_NOT_FOUND", "That deni was not found for this customer."));
            long owed = balances.get(chosen.getId()).outstandingMinor();
            if (amount > owed) {
                throw ApiException.conflict("OVERPAYMENT", owed == 0
                        ? "That deni is already settled."
                        : "Only " + Money.ksh(owed) + " is still owed on that deni.");
            }
            targets = List.of(chosen);
        } else {
            targets = active.stream()
                    .filter(d -> balances.get(d.getId()).outstandingMinor() > 0)
                    .sorted(Comparator.comparing(CustomerDebt::getDueDate)
                            .thenComparing(CustomerDebt::getCreatedAt)
                            .thenComparing(CustomerDebt::getId))
                    .toList();
            long totalOwed = targets.stream().mapToLong(d -> balances.get(d.getId()).outstandingMinor()).sum();
            if (amount > totalOwed) {
                throw ApiException.conflict("OVERPAYMENT", totalOwed == 0
                        ? customer.getName() + " does not owe anything."
                        : customer.getName() + " owes only " + Money.ksh(totalOwed) + ".");
            }
        }

        DebtPayment payment = payments.save(new DebtPayment(shop.getBusinessId(), shopId, day.getId(),
                customer.getId(), request.method(), amount, userId, key, hash));

        long left = amount;
        for (CustomerDebt d : targets) {
            if (left == 0) break;
            long take = Math.min(left, balances.get(d.getId()).outstandingMinor());
            if (take <= 0) continue;
            allocationRepo.save(new DebtPaymentAllocation(payment.getId(), d.getId(), customer.getId(), take));
            left -= take;
        }
        payments.flush();

        return new Result(one(payment), false);
    }

    // ----------------------------------------------------------------- reversing

    /** Same rules as reversing a sale: open day only, by the person who recorded it or the owner, once. */
    @Transactional
    public PaymentResponse reverse(UUID userId, UUID shopId, UUID paymentId, String rawReason) {
        AccessService.ShopContext ctx = access.requireShopAccess(userId, shopId);
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.length() < 3) {
            throw ApiException.badRequest("REASON_REQUIRED", "Say why this repayment is being reversed (at least 3 characters).");
        }

        ShopDay openDay = days.findLockedForShare(shopId, DayStatus.OPEN).orElse(null);
        DebtPayment payment = payments.findForUpdate(paymentId, shopId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "No such repayment"));

        if (ctx.role() != Role.OWNER && !payment.getReceivedBy().equals(userId)) {
            throw ApiException.forbidden("NOT_YOUR_ENTRY",
                    "You can only reverse repayments you recorded yourself. Ask the owner.");
        }
        if (openDay == null || !openDay.getId().equals(payment.getShopDayId())) {
            throw ApiException.conflict("DAY_CLOSED",
                    "This repayment belongs to a day that is already closed. Ask the owner to record an adjustment.");
        }
        if (reversalRepo.existsByPaymentId(paymentId)) {
            throw ApiException.conflict("ALREADY_REVERSED", "This repayment was already reversed.");
        }

        reversalRepo.saveAndFlush(new DebtPaymentReversal(paymentId, shopId, openDay.getId(), reason, userId));
        return one(payment);
    }

    // ------------------------------------------------------------------- listing

    /** Defaults to the shop's current business day. Newest first. */
    @Transactional(readOnly = true)
    public DebtViews.PaymentPage list(UUID userId, UUID shopId, LocalDate date, int page, int size) {
        access.requireShopAccess(userId, shopId);
        LocalDate target = date != null ? date
                : days.findByShopIdAndStatus(shopId, DayStatus.OPEN).map(ShopDay::getBusinessDate).orElse(clock.today());
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<DebtPayment> result = days.findByShopIdAndBusinessDate(shopId, target)
                .map(day -> payments.findByShopDayId(day.getId(), pageable))
                .orElse(Page.empty(pageable));
        return new DebtViews.PaymentPage(target, responses(result.getContent()), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    // ------------------------------------------------------------------- helpers

    private Optional<Result> replayOf(UUID shopId, UUID key, String hash) {
        return payments.findByShopIdAndIdempotencyKey(shopId, key).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw ApiException.conflict("IDEMPOTENCY_CONFLICT",
                        "That Idempotency-Key was already used for a different payment.");
            }
            return new Result(one(existing), true);
        });
    }

    PaymentResponse one(DebtPayment payment) {
        return responses(List.of(payment)).get(0);
    }

    /** Builds responses for many repayments with a handful of queries. */
    List<PaymentResponse> responses(List<DebtPayment> list) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(DebtPayment::getId).toList();

        Map<UUID, List<DebtPaymentAllocation>> partsByPayment = allocationRepo.findByPaymentIdIn(ids).stream()
                .collect(Collectors.groupingBy(DebtPaymentAllocation::getPaymentId));
        Set<UUID> debtIds = partsByPayment.values().stream().flatMap(List::stream)
                .map(DebtPaymentAllocation::getDebtId).collect(Collectors.toSet());
        Map<UUID, CustomerDebt> debtMap = debts.findAllById(debtIds).stream()
                .collect(Collectors.toMap(CustomerDebt::getId, Function.identity()));
        Map<UUID, Sale> saleMap = sales.findAllById(
                        debtMap.values().stream().map(CustomerDebt::getSaleId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Sale::getId, Function.identity()));
        Map<UUID, DebtService.Balance> balances = debtService.balances(debtMap.values());

        Set<UUID> customerIds = list.stream().map(DebtPayment::getCustomerId).collect(Collectors.toSet());
        Map<UUID, Customer> customerMap = customers.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<UUID, Long> owed = debtService.outstanding(customerIds);
        Map<UUID, LocalDate> dates = days.findAllById(
                        list.stream().map(DebtPayment::getShopDayId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ShopDay::getId, ShopDay::getBusinessDate));

        Map<UUID, DebtPaymentReversal> reversalByPayment = reversalRepo.findByPaymentIdIn(ids).stream()
                .collect(Collectors.toMap(DebtPaymentReversal::getPaymentId, Function.identity()));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(p -> userIds.add(p.getReceivedBy()));
        reversalByPayment.values().forEach(r -> userIds.add(r.getReversedBy()));
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        return list.stream().map(p -> {
            List<PaymentResponse.Allocation> lines = partsByPayment.getOrDefault(p.getId(), List.of()).stream()
                    .sorted(Comparator.comparing((DebtPaymentAllocation a) -> debtMap.get(a.getDebtId()).getDueDate())
                            .thenComparing(a -> debtMap.get(a.getDebtId()).getCreatedAt()))
                    .map(a -> {
                        CustomerDebt d = debtMap.get(a.getDebtId());
                        return new PaymentResponse.Allocation(d.getId(), saleMap.get(d.getSaleId()).getReceiptNo(),
                                a.getAmountMinor(), balances.get(d.getId()).outstandingMinor(), d.getDueDate());
                    }).toList();
            DebtPaymentReversal r = reversalByPayment.get(p.getId());
            PaymentResponse.Reversal reversal = r == null ? null : new PaymentResponse.Reversal(
                    r.getReason(), names.getOrDefault(r.getReversedBy(), ""), r.getCreatedAt());
            Customer customer = customerMap.get(p.getCustomerId());
            return new PaymentResponse(p.getId(), customer.getId(), customer.getName(), p.getMethod(),
                    p.getAmountMinor(), p.getShopId(), dates.get(p.getShopDayId()),
                    names.getOrDefault(p.getReceivedBy(), ""), p.getCreatedAt(), lines,
                    owed.getOrDefault(customer.getId(), 0L), reversal);
        }).toList();
    }
}
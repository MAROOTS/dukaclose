// DebtPaymentService.java
package com.dukaclose.api.deni;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.Money;
import com.dukaclose.api.day.DayStatus;
import com.dukaclose.api.day.ShopDay;
import com.dukaclose.api.day.ShopDayRepository;
import com.dukaclose.api.sales.Sale;
import com.dukaclose.api.sales.SaleRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
    private final DebtService debtService;
    private final SaleRepository sales;
    private final UserRepository users;

    public record Result(PaymentResponse payment, boolean replayed) {}

    @Transactional
    public Result pay(UUID userId, UUID shopId, UUID key, RepayRequest request) {
        Shop shop = access.requireShopAccess(userId, shopId).shop();
        String hash = fingerprint(request);

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

        return new Result(toResponse(payment), false);
    }

    // ------------------------------------------------------------------- helpers

    private Optional<Result> replayOf(UUID shopId, UUID key, String hash) {
        return payments.findByShopIdAndIdempotencyKey(shopId, key).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw ApiException.conflict("IDEMPOTENCY_CONFLICT",
                        "That Idempotency-Key was already used for a different payment.");
            }
            return new Result(toResponse(existing), true);
        });
    }

    private PaymentResponse toResponse(DebtPayment p) {
        List<DebtPaymentAllocation> parts = allocationRepo.findByPaymentIdIn(List.of(p.getId()));
        Map<UUID, CustomerDebt> debtMap = debts.findAllById(
                        parts.stream().map(DebtPaymentAllocation::getDebtId).toList()).stream()
                .collect(Collectors.toMap(CustomerDebt::getId, Function.identity()));
        Map<UUID, Sale> saleMap = sales.findAllById(
                        debtMap.values().stream().map(CustomerDebt::getSaleId).toList()).stream()
                .collect(Collectors.toMap(Sale::getId, Function.identity()));
        Map<UUID, DebtService.Balance> balances = debtService.balances(debtMap.values());

        List<PaymentResponse.Allocation> lines = parts.stream()
                .sorted(Comparator.comparing((DebtPaymentAllocation a) -> debtMap.get(a.getDebtId()).getDueDate())
                        .thenComparing(a -> debtMap.get(a.getDebtId()).getCreatedAt()))
                .map(a -> {
                    CustomerDebt d = debtMap.get(a.getDebtId());
                    return new PaymentResponse.Allocation(d.getId(), saleMap.get(d.getSaleId()).getReceiptNo(),
                            a.getAmountMinor(), balances.get(d.getId()).outstandingMinor(), d.getDueDate());
                }).toList();

        Customer customer = customers.findById(p.getCustomerId()).orElseThrow();
        long owed = debtService.outstanding(List.of(customer.getId())).getOrDefault(customer.getId(), 0L);
        LocalDate date = days.findById(p.getShopDayId()).map(ShopDay::getBusinessDate).orElseThrow();
        String by = users.findById(p.getReceivedBy()).map(User::getDisplayName).orElse("");

        return new PaymentResponse(p.getId(), customer.getId(), customer.getName(), p.getMethod(),
                p.getAmountMinor(), p.getShopId(), date, by, p.getCreatedAt(), lines, owed);
    }

    private static String fingerprint(RepayRequest r) {
        String text = r.customerId() + ":" + r.method() + ":" + r.amountMinor() + ":" + r.debtId();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
// DebtLedgerService.java
package com.dukaclose.api.deni;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.business.ShopRepository;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.day.BusinessClock;
import com.dukaclose.api.sales.Sale;
import com.dukaclose.api.sales.SaleRepository;
import com.dukaclose.api.deni.DebtViews.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DebtLedgerService {

    /** Customers who owe something, with the overdue part and the earliest due date. Params: today, business id. */
    private static final String GROUPED = """
        SELECT c.id, c.name, c.phone, c.credit_limit_minor,
               SUM(b.balance) AS outstanding,
               COALESCE(SUM(b.balance) FILTER (WHERE b.due_date < ?), 0) AS overdue,
               MIN(b.due_date) AS next_due
          FROM (SELECT d.customer_id, d.due_date,
                       d.original_amount_minor
                         - COALESCE((SELECT SUM(a.amount_minor) FROM debt_payment_allocations a
                                      WHERE a.debt_id = d.id
                                        AND NOT EXISTS (SELECT 1 FROM debt_payment_reversals r
                                                         WHERE r.payment_id = a.payment_id)), 0)
                         - COALESCE((SELECT SUM(w.amount_minor) FROM debt_write_offs w WHERE w.debt_id = d.id), 0) AS balance
                  FROM customer_debts d
                 WHERE d.business_id = ? AND d.status = 'ACTIVE') b
          JOIN customers c ON c.id = b.customer_id
         WHERE b.balance > 0
         GROUP BY c.id, c.name, c.phone, c.credit_limit_minor
        """;

    private final AccessService access;
    private final CustomerRepository customers;
    private final CustomerDebtRepository debts;
    private final DebtPaymentRepository payments;
    private final DebtPaymentAllocationRepository paymentAllocations;
    private final DebtWriteOffRepository writeOffs;
    private final DebtService debtService;
    private final SaleRepository sales;
    private final ShopRepository shops;
    private final UserRepository users;
    private final BusinessClock clock;
    private final JdbcTemplate jdbc;
    private final DebtPaymentReversalRepository paymentReversals;

    // ---------------------------------------------------------------- the ledger

    @Transactional(readOnly = true)
    public LedgerPage list(UUID userId, UUID shopId, int page, int size) {
        Shop shop = access.requireShopAccess(userId, shopId).shop();
        LocalDate today = clock.today();
        UUID business = shop.getBusinessId();
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 50);

        long[] totals = jdbc.queryForObject(
                "SELECT COALESCE(SUM(outstanding), 0), COALESCE(SUM(overdue), 0), COUNT(*) FROM (" + GROUPED + ") t",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, today, business);

        List<LedgerRow> rows = jdbc.query(GROUPED + " ORDER BY next_due ASC, c.name ASC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    long outstanding = rs.getLong("outstanding");
                    long limit = rs.getLong("credit_limit_minor");
                    Long creditLimit = rs.wasNull() ? null : limit;
                    return new LedgerRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("phone"),
                            outstanding, rs.getLong("overdue"), rs.getObject("next_due", LocalDate.class),
                            creditLimit, creditLimit != null && outstanding > creditLimit);
                }, today, business, s, (long) p * s);

        int totalPages = (int) ((totals[2] + s - 1) / s);
        return new LedgerPage(today, totals[0], totals[1], totals[2], rows, p, s, totalPages);
    }

    // ------------------------------------------------------------ one customer

    @Transactional(readOnly = true)
    public CustomerLedger customer(UUID userId, UUID shopId, UUID customerId) {
        Shop shop = access.requireShopAccess(userId, shopId).shop();
        Customer c = customers.findByIdAndBusinessId(customerId, shop.getBusinessId())
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "No such customer"));
        LocalDate today = clock.today();

        List<CustomerDebt> recentDebts = debts.findTop100ByCustomerIdOrderByCreatedAtDesc(customerId);
        List<DebtPayment> recentPayments = payments.findTop100ByCustomerIdOrderByCreatedAtDesc(customerId);
        List<DebtWriteOff> recentOffs = writeOffs.findTop100ByCustomerIdOrderByCreatedAtDesc(customerId);
        Map<UUID, List<DebtPaymentAllocation>> partsByPayment = paymentAllocations
                .findByPaymentIdIn(recentPayments.stream().map(DebtPayment::getId).toList()).stream()
                .collect(Collectors.groupingBy(DebtPaymentAllocation::getPaymentId));
        Map<UUID, DebtPaymentReversal> reversalByPayment = paymentReversals
                .findByPaymentIdIn(recentPayments.stream().map(DebtPayment::getId).toList()).stream()
                .collect(Collectors.toMap(DebtPaymentReversal::getPaymentId, Function.identity()));
        // payments and write-offs can refer to debts older than the 100 shown, so load every debt we mention
        Set<UUID> debtIds = new HashSet<>();
        recentDebts.forEach(d -> debtIds.add(d.getId()));
        partsByPayment.values().forEach(l -> l.forEach(a -> debtIds.add(a.getDebtId())));
        recentOffs.forEach(w -> debtIds.add(w.getDebtId()));
        Map<UUID, CustomerDebt> debtMap = debts.findAllById(debtIds).stream()
                .collect(Collectors.toMap(CustomerDebt::getId, Function.identity()));

        Map<UUID, Sale> saleMap = sales.findAllById(
                        debtMap.values().stream().map(CustomerDebt::getSaleId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Sale::getId, Function.identity()));
        Set<UUID> shopIds = Stream.concat(debtMap.values().stream().map(CustomerDebt::getShopId),
                recentPayments.stream().map(DebtPayment::getShopId)).collect(Collectors.toSet());
        Map<UUID, String> shopNames = shops.findAllById(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Shop::getName));
        Set<UUID> userIds = new HashSet<>();
        recentPayments.forEach(p -> userIds.add(p.getReceivedBy()));
        recentOffs.forEach(w -> userIds.add(w.getWrittenOffBy()));
        reversalByPayment.values().forEach(r -> userIds.add(r.getReversedBy()));
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        Map<UUID, DebtService.Balance> balances = debtService.balances(recentDebts);
        List<DebtLine> debtLines = recentDebts.stream()
                .map(d -> line(d, balances.get(d.getId()), saleMap, shopNames, today)).toList();

        Function<UUID, PaymentReversalView> reversalView = paymentId -> {
            DebtPaymentReversal r = reversalByPayment.get(paymentId);
            return r == null ? null
                    : new PaymentReversalView(r.getReason(), names.getOrDefault(r.getReversedBy(), ""), r.getCreatedAt());
        };
        List<PaymentLine> paymentLines = recentPayments.stream().map(p -> new PaymentLine(
                p.getId(), p.getMethod(), p.getAmountMinor(), names.getOrDefault(p.getReceivedBy(), ""),
                shopNames.getOrDefault(p.getShopId(), ""), p.getCreatedAt(),
                partsByPayment.getOrDefault(p.getId(), List.of()).stream()
                        .sorted(Comparator.comparing((DebtPaymentAllocation a) -> debtMap.get(a.getDebtId()).getDueDate()))
                        .map(a -> new PaymentAllocationLine(a.getDebtId(),
                                saleMap.get(debtMap.get(a.getDebtId()).getSaleId()).getReceiptNo(), a.getAmountMinor()))
                        .toList(),
                reversalView.apply(p.getId()))).toList();

        List<WriteOffLine> offLines = recentOffs.stream().map(w -> new WriteOffLine(
                w.getDebtId(), saleMap.get(debtMap.get(w.getDebtId()).getSaleId()).getReceiptNo(),
                w.getAmountMinor(), w.getReason(), names.getOrDefault(w.getWrittenOffBy(), ""), w.getCreatedAt())).toList();

        long outstanding = debtService.outstanding(List.of(customerId)).getOrDefault(customerId, 0L);
        long overdue = debtLines.stream().filter(DebtLine::overdue).mapToLong(DebtLine::balanceMinor).sum();
        Long limit = c.getCreditLimitMinor();
        CustomerView view = new CustomerView(c.getId(), c.getName(), c.getPhone(), outstanding, limit,
                limit != null && outstanding > limit);
        return new CustomerLedger(view, overdue, debtLines, paymentLines, offLines);
    }

    /** One debt, in the shape the ledger shows it. */
    @Transactional(readOnly = true)
    public DebtLine line(CustomerDebt d) {
        DebtService.Balance balance = debtService.balances(List.of(d)).get(d.getId());
        Sale sale = sales.findById(d.getSaleId()).orElseThrow();
        String shopName = shops.findById(d.getShopId()).map(Shop::getName).orElse("");
        return line(d, balance, Map.of(sale.getId(), sale), Map.of(d.getShopId(), shopName), clock.today());
    }

    private DebtLine line(CustomerDebt d, DebtService.Balance b, Map<UUID, Sale> saleMap,
                          Map<UUID, String> shopNames, LocalDate today) {
        boolean voided = d.getStatus() == DebtStatus.VOID;
        long balance = voided ? 0 : b.outstandingMinor();
        return new DebtLine(d.getId(), d.getSaleId(), saleMap.get(d.getSaleId()).getReceiptNo(),
                shopNames.getOrDefault(d.getShopId(), ""), d.getOriginalAmountMinor(), b.paidMinor(),
                b.writtenOffMinor(), balance, d.getDueDate(), !voided && balance > 0 && d.getDueDate().isBefore(today),
                voided, d.getCreatedAt());
    }
}
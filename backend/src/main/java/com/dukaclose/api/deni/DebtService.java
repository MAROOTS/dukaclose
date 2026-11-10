package com.dukaclose.api.deni;

import com.dukaclose.api.common.ApiException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
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
public class DebtService {

    private final CustomerDebtRepository debts;
    private final DebtPaymentAllocationRepository allocations;
    private final DebtWriteOffRepository writeOffs;

    /** What one debt looks like: what it was, what was paid, what was written off. */
    public record Balance(long originalMinor, long paidMinor, long writtenOffMinor) {
        public long outstandingMinor() {
            return originalMinor - paidMinor - writtenOffMinor;
        }
    }

    @Transactional
    public CustomerDebt create(UUID businessId, UUID shopId, UUID saleId, UUID customerId,
                               long amountMinor, LocalDate dueDate) {
        return debts.save(new CustomerDebt(businessId, shopId, saleId, customerId, amountMinor, dueDate));
    }

    /**
     * Called before a sale is reversed. Locks the debt, so a repayment arriving at the same moment must wait,
     * and refuses when the customer has already paid or been written off against it.
     */
    @Transactional
    public void lockAndCheckReversible(UUID saleId) {
        debts.findBySaleIdForUpdate(saleId).ifPresent(debt -> {
            if (allocations.existsByDebtId(debt.getId()) || writeOffs.existsByDebtId(debt.getId())) {
                throw ApiException.conflict("DEBT_HAS_PAYMENTS",
                        "The customer has already paid something against this deni, so the sale cannot be reversed here. "
                                + "Ask the owner.");
            }
        });
    }

    /** Reversing a sale cancels the receivable it created. */
    @Transactional
    public void voidForSale(UUID saleId) {
        debts.findBySaleIdForUpdate(saleId).ifPresent(CustomerDebt::markVoid);
    }

    @Transactional(readOnly = true)
    public Map<UUID, CustomerDebt> forSales(Collection<UUID> saleIds) {
        return debts.findBySaleIdIn(saleIds).stream()
                .collect(Collectors.toMap(CustomerDebt::getSaleId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Balance> balances(Collection<CustomerDebt> list) {
        Map<UUID, Balance> result = new HashMap<>();
        if (list.isEmpty()) return result;
        List<UUID> ids = list.stream().map(CustomerDebt::getId).toList();
        Map<UUID, Long> paid = toMap(allocations.sumByDebt(ids));
        Map<UUID, Long> off = toMap(writeOffs.sumByDebt(ids));
        for (CustomerDebt d : list) {
            result.put(d.getId(), new Balance(d.getOriginalAmountMinor(),
                    paid.getOrDefault(d.getId(), 0L), off.getOrDefault(d.getId(), 0L)));
        }
        return result;
    }

    /**
     * What each customer owes right now: active debts, less repayments, less write-offs.
     * Every screen that shows a balance gets it from here. Customers who owe nothing are simply absent.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> outstanding(Collection<UUID> customerIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (customerIds.isEmpty()) return result;
        Map<UUID, Long> original = toMap(debts.activeTotals(customerIds));
        Map<UUID, Long> paid = toMap(allocations.sumByCustomer(customerIds));
        Map<UUID, Long> off = toMap(writeOffs.sumByCustomer(customerIds));
        original.forEach((id, total) ->
                result.put(id, total - paid.getOrDefault(id, 0L) - off.getOrDefault(id, 0L)));
        return result;
    }

    private static Map<UUID, Long> toMap(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }
}
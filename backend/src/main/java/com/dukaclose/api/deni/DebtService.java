// DebtService.java
package com.dukaclose.api.deni;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
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

    @Transactional
    public CustomerDebt create(UUID businessId, UUID shopId, UUID saleId, UUID customerId,
                               long amountMinor, LocalDate dueDate) {
        return debts.save(new CustomerDebt(businessId, shopId, saleId, customerId, amountMinor, dueDate));
    }

    /** Reversing a sale cancels the receivable it created. */
    @Transactional
    public void voidForSale(UUID saleId) {
        debts.findBySaleId(saleId).ifPresent(CustomerDebt::markVoid);
    }

    @Transactional(readOnly = true)
    public Map<UUID, CustomerDebt> forSales(Collection<UUID> saleIds) {
        return debts.findBySaleIdIn(saleIds).stream()
                .collect(Collectors.toMap(CustomerDebt::getSaleId, Function.identity()));
    }

    /**
     * What each customer owes right now. This is the ONE place balances are worked out.
     * For now that is the sum of active debts. Step 16 subtracts repayments and write-offs here.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> outstanding(Collection<UUID> customerIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (customerIds.isEmpty()) return result;
        for (Object[] row : debts.activeTotals(customerIds)) {
            result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }
}
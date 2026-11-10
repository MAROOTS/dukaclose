// DebtWriteOffService.java
package com.dukaclose.api.deni;

import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.Money;
import com.dukaclose.api.day.BusinessClock;
import com.dukaclose.api.deni.DebtViews.WriteOffResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DebtWriteOffService {

    private final AccessService access;
    private final CustomerDebtRepository debts;
    private final DebtWriteOffRepository writeOffs;
    private final DebtService debtService;
    private final DebtLedgerService ledger;
    private final BusinessClock clock;

    @Transactional
    public WriteOffResponse writeOff(UUID ownerId, UUID businessId, UUID debtId, WriteOffRequest request) {
        access.requireBusinessOwner(ownerId, businessId);

        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.length() < 3) {
            throw ApiException.badRequest("REASON_REQUIRED", "Say why the debt is being written off (at least 3 characters).");
        }

        CustomerDebt debt = debts.findForUpdate(debtId, businessId)
                .orElseThrow(() -> ApiException.notFound("DEBT_NOT_FOUND", "No such deni"));
        if (debt.getStatus() == DebtStatus.VOID) {
            throw ApiException.conflict("DEBT_VOID", "This deni was cancelled together with its sale.");
        }

        long balance = debtService.balances(List.of(debt)).get(debt.getId()).outstandingMinor();
        if (balance <= 0) {
            throw ApiException.conflict("NOTHING_OWED", "This deni is already settled.");
        }
        long amount = request.amountMinor() == null ? balance : request.amountMinor();
        if (amount > balance) {
            throw ApiException.conflict("WRITE_OFF_TOO_LARGE", "Only " + Money.ksh(balance) + " is still owed on this deni.");
        }

        writeOffs.saveAndFlush(new DebtWriteOff(businessId, debt.getId(), debt.getCustomerId(), amount, reason,
                ownerId, clock.today()));

        long owed = debtService.outstanding(List.of(debt.getCustomerId())).getOrDefault(debt.getCustomerId(), 0L);
        return new WriteOffResponse(ledger.line(debt), owed);
    }
}
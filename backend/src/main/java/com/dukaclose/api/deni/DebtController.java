package com.dukaclose.api.deni;

import com.dukaclose.api.auth.AppUserDetails;
import com.dukaclose.api.deni.DebtViews.*;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DebtController {

    private final DebtPaymentService paymentService;
    private final DebtLedgerService ledgerService;
    private final DebtWriteOffService writeOffService;

    /** 201 for a new payment, 200 when the same Idempotency-Key was already used for this exact payment. */
    @PostMapping("/shops/{shopId}/debt-payments")
    public ResponseEntity<PaymentResponse> pay(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                               @RequestHeader("Idempotency-Key") UUID key,
                                               @Valid @RequestBody RepayRequest body) {
        DebtPaymentService.Result result = paymentService.pay(me.userId(), shopId, key, body);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.payment());
    }

    /** Everyone who owes something, earliest due date first. */
    @GetMapping("/shops/{shopId}/debts")
    public LedgerPage ledger(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        return ledgerService.list(me.userId(), shopId, page, size);
    }

    @GetMapping("/shops/{shopId}/customers/{customerId}/ledger")
    public CustomerLedger customerLedger(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                         @PathVariable UUID customerId) {
        return ledgerService.customer(me.userId(), shopId, customerId);
    }

    @PostMapping("/businesses/{businessId}/debts/{debtId}/write-off")
    @ResponseStatus(HttpStatus.CREATED)
    public WriteOffResponse writeOff(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                     @PathVariable UUID debtId, @Valid @RequestBody WriteOffRequest body) {
        return writeOffService.writeOff(me.userId(), businessId, debtId, body);
    }
}
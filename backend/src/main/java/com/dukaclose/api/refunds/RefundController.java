// RefundController.java
package com.dukaclose.api.refunds;

import com.dukaclose.api.auth.AppUserDetails;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/sales/{saleId}/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService service;

    /** What can still be refunded on this sale and what was refunded already. Anyone on the shop may read it. */
    @GetMapping
    public RefundOptions options(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                 @PathVariable UUID saleId) {
        return service.options(me.userId(), shopId, saleId);
    }

    /** The owner's screen asks the server for the numbers, so it never does the refund arithmetic itself. */
    @PostMapping("/quote")
    public RefundQuote quote(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                             @PathVariable UUID saleId, @Valid @RequestBody RefundQuoteRequest body) {
        return service.quote(me.userId(), shopId, saleId, body.lines());
    }

    /** 201 for a new refund, 200 when the same Idempotency-Key was already used for this exact refund. */
    @PostMapping
    public ResponseEntity<RefundResponse> refund(@AuthenticationPrincipal AppUserDetails me,
                                                 @PathVariable UUID shopId, @PathVariable UUID saleId,
                                                 @RequestHeader("Idempotency-Key") UUID key,
                                                 @Valid @RequestBody RefundRequest body) {
        RefundService.Result result = service.create(me.userId(), shopId, saleId, key, body);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.refund());
    }
}
package com.dukaclose.api.sales;

import com.dukaclose.api.auth.AppUserDetails;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService service;
    private final SaleReversalService reversalService;

    /** 201 for a new sale, 200 when the same Idempotency-Key was already used for this exact sale. */
    @PostMapping
    public ResponseEntity<SaleResponse> create(@AuthenticationPrincipal AppUserDetails me,
                                               @PathVariable UUID shopId,
                                               @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                               @Valid @RequestBody CreateSaleRequest body) {
        SaleService.SaleResult result = service.create(me.userId(), shopId, idempotencyKey, body);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.sale());
    }

    /** Defaults to the shop's current business day. Newest receipt first. */
    @GetMapping
    public SalePage list(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return service.list(me.userId(), shopId, date, page, size);
    }

    @GetMapping("/{saleId}")
    public SaleResponse get(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                            @PathVariable UUID saleId) {
        return service.get(me.userId(), shopId, saleId);
    }

    @PostMapping("/{saleId}/reverse")
    public SaleResponse reverse(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                @PathVariable UUID saleId, @Valid @RequestBody ReverseSaleRequest body) {
        return reversalService.reverse(me.userId(), shopId, saleId, body.reason());
    }
}
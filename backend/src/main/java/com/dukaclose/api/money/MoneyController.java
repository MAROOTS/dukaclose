package com.dukaclose.api.money;

import com.dukaclose.api.auth.AppUserDetails;
import com.dukaclose.api.common.ReasonRequest;
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
@RequestMapping("/api/v1/shops/{shopId}/money-movements")
@RequiredArgsConstructor
public class MoneyController {

    private final MoneyService service;

    /** 201 for a new entry, 200 when the same Idempotency-Key was already used for this exact entry. */
    @PostMapping
    public ResponseEntity<MoneyResponse> create(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                                @RequestHeader("Idempotency-Key") UUID key,
                                                @Valid @RequestBody MoneyRequest body) {
        MoneyService.Result result = service.create(me.userId(), shopId, key, body);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.movement());
    }

    @GetMapping
    public MoneyResponse.Page list(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return service.list(me.userId(), shopId, date, page, size);
    }

    @PostMapping("/{movementId}/reverse")
    public MoneyResponse reverse(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                 @PathVariable UUID movementId, @Valid @RequestBody ReasonRequest body) {
        return service.reverse(me.userId(), shopId, movementId, body.reason());
    }
}
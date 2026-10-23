// CashierController.java
package com.dukaclose.api.business;

import com.dukaclose.api.auth.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CashierController {

    private final CashierService cashiers;

    @PostMapping("/businesses/{businessId}/cashiers")
    @ResponseStatus(HttpStatus.CREATED)
    public CashierResponse create(@AuthenticationPrincipal AppUserDetails me,
                                  @PathVariable UUID businessId,
                                  @Valid @RequestBody CreateCashierRequest body) {
        return cashiers.create(me.userId(), businessId, body);
    }

    @GetMapping("/shops/{shopId}/cashiers")
    public List<CashierResponse> list(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId) {
        return cashiers.listForShop(me.userId(), shopId);
    }

    @PostMapping("/shops/{shopId}/cashiers/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@AuthenticationPrincipal AppUserDetails me,
                       @PathVariable UUID shopId, @PathVariable UUID userId) {
        cashiers.assign(me.userId(), shopId, userId);
    }

    @DeleteMapping("/shops/{shopId}/cashiers/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID shopId, @PathVariable UUID userId) {
        cashiers.unassign(me.userId(), shopId, userId);
    }
}
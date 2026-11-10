// CustomerController.java
package com.dukaclose.api.deni;

import com.dukaclose.api.auth.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    /** For the customer picker on the sell screen. Needs at least 2 characters. */
    @GetMapping("/shops/{shopId}/customers")
    public List<CustomerView> search(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                     @RequestParam(defaultValue = "") String q) {
        return service.search(me.userId(), shopId, q);
    }

    @PutMapping("/businesses/{businessId}/customers/{customerId}/credit-limit")
    public CustomerView setCreditLimit(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                       @PathVariable UUID customerId,
                                       @Valid @RequestBody SetCreditLimitRequest body) {
        return service.setCreditLimit(me.userId(), businessId, customerId, body.creditLimitMinor());
    }
}
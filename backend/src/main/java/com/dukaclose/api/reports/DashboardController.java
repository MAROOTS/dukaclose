// DashboardController.java
package com.dukaclose.api.reports;

import com.dukaclose.api.auth.AppUserDetails;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    /** Owner only. Leave shopId out for all shops. asOf looks back at an earlier day, week or month. */
    @GetMapping
    public DashboardResponse get(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                 @RequestParam(defaultValue = "today") String period,
                                 @RequestParam(required = false) UUID shopId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.dashboard(me.userId(), businessId, period, shopId, asOf);
    }
}
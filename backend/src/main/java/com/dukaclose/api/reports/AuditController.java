// AuditController.java
package com.dukaclose.api.reports;

import com.dukaclose.api.auth.AppUserDetails;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/audit-events")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService service;

    /** Owner only. With no dates, the last seven days. kind may be several, separated by commas. */
    @GetMapping
    public AuditEvent.Page list(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                @RequestParam(required = false) UUID shopId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(required = false) String category,
                                @RequestParam(required = false) String kind,
                                @RequestParam(required = false) UUID actorId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return service.list(me.userId(), businessId, shopId, from, to, category, kind, actorId, page, size);
    }
}
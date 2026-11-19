// ClosedDaysController.java
package com.dukaclose.api.closing;

import com.dukaclose.api.auth.AppUserDetails;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/closes")
@RequiredArgsConstructor
public class ClosedDaysController {

    private final ClosedDayService service;

    /** Newest closed day first. */
    @GetMapping
    public ClosedDayView.Page list(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return service.list(me.userId(), shopId, page, size);
    }

    @GetMapping("/{date}")
    public ClosedDayView get(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                             @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.get(me.userId(), shopId, date);
    }
}
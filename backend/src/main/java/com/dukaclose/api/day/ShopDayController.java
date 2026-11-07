// ShopDayController.java
package com.dukaclose.api.day;

import com.dukaclose.api.auth.AppUserDetails;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/days")
@RequiredArgsConstructor
public class ShopDayController {

    private final ShopDayService service;

    @GetMapping("/current")
    public DayStatusResponse current(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId) {
        return service.status(me.userId(), shopId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShopDayResponse open(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                @Valid @RequestBody OpenDayRequest body) {
        return service.open(me.userId(), shopId, body);
    }
}
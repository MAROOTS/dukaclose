// ShopController.java
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
@RequestMapping("/api/v1/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShopResponse create(@AuthenticationPrincipal AppUserDetails me,
                               @Valid @RequestBody CreateShopRequest body) {
        return shopService.create(me.userId(), body);
    }

    @GetMapping
    public List<ShopResponse> list(@AuthenticationPrincipal AppUserDetails me) {
        return shopService.list(me.userId());
    }

    @GetMapping("/{shopId}")
    public ShopResponse get(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId) {
        return shopService.get(me.userId(), shopId);
    }
}
// StockController.java
package com.dukaclose.api.inventory;

import com.dukaclose.api.auth.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/inventory/{itemId}")
@RequiredArgsConstructor
public class StockController {

    private final StockService stock;

    @PostMapping("/opening")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse opening(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                    @PathVariable UUID itemId, @Valid @RequestBody StockInRequest body) {
        return stock.openingStock(me.userId(), shopId, itemId, body);
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse receive(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                    @PathVariable UUID itemId, @Valid @RequestBody StockInRequest body) {
        return stock.receive(me.userId(), shopId, itemId, body);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse adjust(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                   @PathVariable UUID itemId, @Valid @RequestBody AdjustStockRequest body) {
        return stock.adjust(me.userId(), shopId, itemId, body);
    }

    @GetMapping("/movements")
    public List<MovementResponse> movements(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                                            @PathVariable UUID itemId,
                                            @RequestParam(defaultValue = "50") int limit) {
        return stock.history(me.userId(), shopId, itemId, limit);
    }
}
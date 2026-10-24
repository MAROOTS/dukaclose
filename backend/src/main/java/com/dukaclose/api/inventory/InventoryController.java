// InventoryController.java
package com.dukaclose.api.inventory;

import com.dukaclose.api.auth.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public List<InventoryRow> list(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId) {
        return inventoryService.list(me.userId(), shopId);
    }

    @PutMapping("/{itemId}")
    public InventoryRow update(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId,
                               @PathVariable UUID itemId, @Valid @RequestBody UpdateShopItemRequest body) {
        return inventoryService.update(me.userId(), shopId, itemId, body);
    }
}
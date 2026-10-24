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
@RequestMapping("/api/v1/businesses/{businessId}/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                               @Valid @RequestBody CreateItemRequest body) {
        return itemService.create(me.userId(), businessId, body);
    }

    @GetMapping
    public List<ItemResponse> list(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId) {
        return itemService.list(me.userId(), businessId);
    }

    @PutMapping("/{itemId}")
    public ItemResponse update(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                               @PathVariable UUID itemId, @Valid @RequestBody UpdateItemRequest body) {
        return itemService.update(me.userId(), businessId, itemId, body);
    }
}
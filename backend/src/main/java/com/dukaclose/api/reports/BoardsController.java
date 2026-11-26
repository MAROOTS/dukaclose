// BoardsController.java
package com.dukaclose.api.reports;

import com.dukaclose.api.auth.AppUserDetails;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}")
@RequiredArgsConstructor
public class BoardsController {

    private final BoardsService service;

    @GetMapping("/close-board")
    public OwnerBoards.CloseBoard closeBoard(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                             @RequestParam(defaultValue = "30") int days) {
        return service.closeBoard(me.userId(), businessId, days);
    }

    @GetMapping("/deni-ageing")
    public OwnerBoards.DeniAgeing deniAgeing(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.deniAgeing(me.userId(), businessId, page, size);
    }

    @GetMapping("/low-stock")
    public OwnerBoards.LowStockPage lowStock(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                             @RequestParam(required = false) UUID shopId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.lowStock(me.userId(), businessId, shopId, page, size);
    }
}
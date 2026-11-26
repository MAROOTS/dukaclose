// DrillDownController.java
package com.dukaclose.api.reports;

import com.dukaclose.api.auth.AppUserDetails;
import com.dukaclose.api.reports.DrillDownRows.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The lists behind the dashboard's numbers. All owner-only. from and to default to today. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}")
@RequiredArgsConstructor
public class DrillDownController {

    private final DrillDownService service;

   // private static final String DATE = "yyyy-MM-dd";

    @GetMapping("/sales")
    public ReportPage<SaleRow> sales(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                     @RequestParam(required = false) UUID shopId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.sales(me.userId(), businessId, shopId, from, to, status, page, size);
    }

    @GetMapping("/money-movements")
    public ReportPage<MoneyRow> money(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                      @RequestParam(required = false) UUID shopId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(defaultValue = "false") boolean expensesOnly,
                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.money(me.userId(), businessId, shopId, from, to, category, expensesOnly, page, size);
    }

    @GetMapping("/debt-payments")
    public ReportPage<CollectionRow> collections(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                                 @RequestParam(required = false) UUID shopId,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.collections(me.userId(), businessId, shopId, from, to, page, size);
    }

    @GetMapping("/refunds")
    public ReportPage<RefundRow> refunds(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                         @RequestParam(required = false) UUID shopId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.refunds(me.userId(), businessId, shopId, from, to, page, size);
    }

    @GetMapping("/write-offs")
    public ReportPage<WriteOffRow> writeOffs(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                             @RequestParam(required = false) UUID shopId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.writeOffs(me.userId(), businessId, shopId, from, to, page, size);
    }

    @GetMapping("/stock-adjustments")
    public ReportPage<AdjustmentRow> adjustments(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID businessId,
                                                 @RequestParam(required = false) UUID shopId,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false) String reason,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.adjustments(me.userId(), businessId, shopId, from, to, reason, page, size);
    }
}
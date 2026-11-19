// CloseController.java
package com.dukaclose.api.closing;

import com.dukaclose.api.auth.AppUserDetails;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/days/current")
@RequiredArgsConstructor
public class CloseController {

    private final ClosePreviewService previewService;
    private final CashCountService countService;
    private final CloseService closeService;

    @GetMapping("/close-preview")
    public ClosePreview preview(@AuthenticationPrincipal AppUserDetails me, @PathVariable UUID shopId) {
        return previewService.preview(me.userId(), shopId);
    }

    /** 201 for a new count, 200 when the same Idempotency-Key was already used for this exact count. */
    @PostMapping("/cash-counts")
    public ResponseEntity<CashCountResponse> count(@AuthenticationPrincipal AppUserDetails me,
                                                   @PathVariable UUID shopId,
                                                   @RequestHeader("Idempotency-Key") UUID key,
                                                   @Valid @RequestBody CashCountRequest body) {
        CashCountService.Result result = countService.record(me.userId(), shopId, key, body);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.count());
    }

    /** 201 for a new close, 200 when the same Idempotency-Key was already used for this exact close. */
    @PostMapping("/close")
    public ResponseEntity<ClosedDayView> close(@AuthenticationPrincipal AppUserDetails me,
                                               @PathVariable UUID shopId,
                                               @RequestHeader("Idempotency-Key") UUID key,
                                               @Valid @RequestBody CloseRequest body) {
        CloseService.Result result = closeService.close(me.userId(), shopId, key, body);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.close());
    }
}
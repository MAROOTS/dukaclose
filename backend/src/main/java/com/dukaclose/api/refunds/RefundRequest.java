package com.dukaclose.api.refunds;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * payouts says how the money was actually returned. It may be empty when the refund is entirely
 * covered by what the customer still owed on that sale.
 */
public record RefundRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @Valid LineRequest> lines,
        @NotNull @Size(max = 3) List<@NotNull @Valid PayoutRequest> payouts,
        @NotBlank @Size(max = 200) String reason) {

    public record LineRequest(
            @NotNull UUID saleLineId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("99999999") BigDecimal quantity,
            @NotNull Disposition disposition) {}

    public record PayoutRequest(
            @NotNull PayoutMethod method,
            @NotNull @Positive @Max(10_000_000_000L) Long amountMinor) {}
}
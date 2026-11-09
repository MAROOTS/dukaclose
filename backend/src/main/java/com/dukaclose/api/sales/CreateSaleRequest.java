// CreateSaleRequest.java
package com.dukaclose.api.sales;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateSaleRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @jakarta.validation.Valid LineRequest> lines,
        @NotEmpty @Size(max = 4) List<@NotNull @jakarta.validation.Valid AllocationRequest> allocations) {

    /** Note what is missing: there is no price. The server decides it. */
    public record LineRequest(
            @NotNull UUID itemId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("99999999") BigDecimal quantity) {}

    public record AllocationRequest(
            @NotNull PaymentMethod method,
            @NotNull @Positive @Max(10_000_000_000L) Long amountMinor) {}
}
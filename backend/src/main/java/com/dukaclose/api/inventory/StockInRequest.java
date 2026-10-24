// StockInRequest.java  (used for both opening stock and receipts)
package com.dukaclose.api.inventory;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record StockInRequest(
        @NotNull UUID requestId,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("99999999") BigDecimal quantity,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("99999999") BigDecimal packs,
        @NotNull @PositiveOrZero @Max(10_000_000_000L) Long totalCostMinor,
        @Size(max = 200) String note) {}
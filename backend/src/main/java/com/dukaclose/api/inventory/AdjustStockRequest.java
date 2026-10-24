// AdjustStockRequest.java
package com.dukaclose.api.inventory;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record AdjustStockRequest(
        @NotNull UUID requestId,
        @NotNull @DecimalMin("-99999999") @DecimalMax("99999999") BigDecimal quantityDelta,
        @NotNull AdjustmentReason reason,
        @Size(max = 200) String note) {}
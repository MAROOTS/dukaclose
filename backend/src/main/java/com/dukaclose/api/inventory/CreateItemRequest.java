package com.dukaclose.api.inventory;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateItemRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull Unit unit,
        @Size(max = 40) String packName,
        @DecimalMin("0.001") @DecimalMax("99999999") BigDecimal packSize,
        @PositiveOrZero @Max(1_000_000_000L) Long initialPriceMinor) {}
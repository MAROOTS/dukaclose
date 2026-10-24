// UpdateShopItemRequest.java
package com.dukaclose.api.inventory;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateShopItemRequest(
        @NotNull @PositiveOrZero @Max(1_000_000_000L) Long priceMinor,
        @NotNull @DecimalMin("0") @DecimalMax("99999999") BigDecimal reorderLevel) {}
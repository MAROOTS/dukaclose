package com.dukaclose.api.inventory;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateItemRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 40) String packName,
        @DecimalMin("0.001") @DecimalMax("99999999") BigDecimal packSize,
        @NotNull Boolean active) {}
// MoneyRequest.java
package com.dukaclose.api.money;

import jakarta.validation.constraints.*;

/** The category decides the direction, so there is no "direction" here. */
public record MoneyRequest(
        @NotNull MoneyCategory category,
        @NotNull MoneyMethod method,
        @NotNull @Positive @Max(10_000_000_000L) Long amountMinor,
        @NotBlank @Size(max = 200) String note) {}
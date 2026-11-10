package com.dukaclose.api.deni;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** A null amount writes off everything still owed on that debt. */
public record WriteOffRequest(
        @Positive @Max(10_000_000_000L) Long amountMinor,
        @NotBlank @Size(max = 200) String reason) {}
package com.dukaclose.api.deni;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** debtId is optional: leave it out to pay the oldest debts first. */
public record RepayRequest(
        @NotNull UUID customerId,
        @NotNull CollectionMethod method,
        @NotNull @Positive @Max(10_000_000_000L) Long amountMinor,
        UUID debtId) {}
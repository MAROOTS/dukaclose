// CashCountRequest.java
package com.dukaclose.api.closing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** An empty drawer is a valid count: zero. */
public record CashCountRequest(@NotNull @PositiveOrZero @Max(10_000_000_000L) Long countedMinor) {}
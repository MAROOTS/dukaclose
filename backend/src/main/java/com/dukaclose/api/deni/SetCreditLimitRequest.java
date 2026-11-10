package com.dukaclose.api.deni;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

/** A null limit means "no limit". */
public record SetCreditLimitRequest(@PositiveOrZero @Max(1_000_000_000L) Long creditLimitMinor) {}
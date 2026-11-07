// OpenDayRequest.java
package com.dukaclose.api.day;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record OpenDayRequest(@NotNull @PositiveOrZero @Max(1_000_000_000L) Long openingFloatMinor) {}
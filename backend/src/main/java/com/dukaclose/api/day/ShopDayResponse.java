// ShopDayResponse.java
package com.dukaclose.api.day;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ShopDayResponse(UUID id, UUID shopId, LocalDate businessDate, DayStatus status,
                              long openingFloatMinor, String openedByName, Instant openedAt, boolean stale) {}
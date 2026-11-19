// DayStatusResponse.java
package com.dukaclose.api.day;

import java.time.LocalDate;

/** suggestedFloatMinor is what the last close counted: what should be in the drawer when opening. */
public record DayStatusResponse(LocalDate today, ShopDayResponse openDay,
                                Long suggestedFloatMinor, LocalDate lastCloseDate) {}
// DayStatusResponse.java
package com.dukaclose.api.day;

import java.time.LocalDate;

/** today = the business date right now. openDay = the shop's open day, or null. */
public record DayStatusResponse(LocalDate today, ShopDayResponse openDay) {}
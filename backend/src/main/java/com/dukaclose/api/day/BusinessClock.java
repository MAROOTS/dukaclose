package com.dukaclose.api.day;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** The one place that decides which business date it is: Nairobi time, never the browser's. */
@Component
public class BusinessClock {
    public static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
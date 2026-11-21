package com.dukaclose.api.day;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** The one place that decides which business date it is: Nairobi time, never the browser's. */
@Component
public class BusinessClock {
    public static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    private final int offsetDays;

    /** duka.dev.date-offset-days pretends it is later, for hand-testing. Ignored unless the dev profile is active. */
    public BusinessClock(@Value("${duka.dev.date-offset-days:0}") int offsetDays, Environment env) {
        this.offsetDays = env.acceptsProfiles(Profiles.of("dev")) ? offsetDays : 0;
    }

    public LocalDate today() {
        return LocalDate.now(ZONE).plusDays(offsetDays);
    }
}
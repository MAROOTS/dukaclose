// CashCountResponse.java
package com.dukaclose.api.closing;

import java.time.Instant;
import java.util.UUID;

/** Shown to the person who just counted: only now do they see what was expected. */
public record CashCountResponse(UUID id, int countNumber, long countedMinor, long expectedMinor,
                                long differenceMinor, String activityToken, String countedByName, Instant createdAt) {}
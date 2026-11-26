// ReportPage.java
package com.dukaclose.api.reports;

import java.time.LocalDate;
import java.util.List;

/** summaryMinor is worked out exactly like the matching dashboard figure, over the same filters. */
public record ReportPage<T>(List<T> items, int page, int size, long totalElements, int totalPages,
                            long summaryMinor, LocalDate from, LocalDate to) {}
// SalePage.java
package com.dukaclose.api.sales;

import java.time.LocalDate;
import java.util.List;

public record SalePage(LocalDate date, List<SaleResponse> items, int page, int size,
                       long totalElements, int totalPages) {}
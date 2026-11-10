package com.dukaclose.api.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateSaleRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @Valid LineRequest> lines,
        @NotEmpty @Size(max = 4) List<@NotNull @Valid AllocationRequest> allocations,
        @Valid CustomerRequest customer,
        LocalDate dueDate) {

    /** Note what is missing: there is no price. The server decides it. */
    public record LineRequest(
            @NotNull UUID itemId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("99999999") BigDecimal quantity) {}

    public record AllocationRequest(
            @NotNull PaymentMethod method,
            @NotNull @Positive @Max(10_000_000_000L) Long amountMinor) {}

    /** Required when part of the sale is deni, and refused otherwise. */
    public record CustomerRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String phone) {}
}
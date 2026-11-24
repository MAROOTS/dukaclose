package com.dukaclose.api.refunds;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RefundQuoteRequest(@NotEmpty @Size(max = 100) List<RefundRequest.@Valid @NotNull LineRequest> lines) {}


package com.dukaclose.api.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateShopRequest(
        @NotNull UUID businessId,
        @NotBlank @Size(max = 120) String name) {}

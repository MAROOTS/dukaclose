package com.dukaclose.api.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateCashierRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank String phone,
        @NotBlank @Size(min = 8, max = 72) String password,   // 72 = bcrypt's byte limit
        @NotEmpty List<UUID> shopIds) {}

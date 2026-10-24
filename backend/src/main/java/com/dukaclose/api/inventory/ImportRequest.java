package com.dukaclose.api.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ImportRequest(
        @NotNull UUID importId,
        @NotBlank @Size(max = 1_000_000) String csv) {}
// auth/LoginRequest.java
package com.dukaclose.api.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {}
package com.dukaclose.api.business;

import java.util.UUID;

public record CashierResponse(UUID userId, String displayName, String phone) {}

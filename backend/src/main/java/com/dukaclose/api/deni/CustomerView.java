package com.dukaclose.api.deni;

import java.util.UUID;

public record CustomerView(UUID id, String name, String phone, long outstandingMinor,
                           Long creditLimitMinor, boolean overLimit) {}
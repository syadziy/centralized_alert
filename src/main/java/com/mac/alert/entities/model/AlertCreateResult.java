package com.mac.alert.entities.model;

import java.time.Instant;
import java.util.UUID;

public record AlertCreateResult(
        UUID alertId,
        String status,
        boolean created,
        Instant createdAt
) {
}
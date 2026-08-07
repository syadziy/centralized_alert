package com.mac.alert.entities.model;

import java.time.Instant;
import java.util.UUID;

public record ExistingAlert(
        UUID alertId,
        String status,
        Instant createdAt
) {
}
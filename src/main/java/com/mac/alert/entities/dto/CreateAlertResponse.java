package com.mac.alert.entities.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateAlertResponse(
        UUID alertId,
        String status,
        boolean created,
        Instant createdAt,
        String message
) {
}
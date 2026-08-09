package com.mac.alert.entities.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertWebNotification(
        UUID alertId,
        String eventType,
        String sourceSystem,
        String subject,
        int priority,
        String status,
        Instant createdAt) {}

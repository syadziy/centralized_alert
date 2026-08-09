package com.mac.alert.entities.dto;

import com.mac.alert.entities.constant.RecipientType;
import java.time.Instant;
import java.util.UUID;

public record RecipientConfigurationResponse(
        UUID id,
        String sourceSystem,
        RecipientType type,
        String email,
        String displayName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {}

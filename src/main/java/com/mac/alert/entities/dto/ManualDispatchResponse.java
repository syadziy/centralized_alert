package com.mac.alert.entities.dto;

import java.util.UUID;

public record ManualDispatchResponse(
        UUID alertId,
        boolean accepted,
        String message
) {
}
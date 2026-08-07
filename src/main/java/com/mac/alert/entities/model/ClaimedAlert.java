package com.mac.alert.entities.model;

import java.util.UUID;

public record ClaimedAlert(
        UUID alertId,
        int attemptNo,
        int maxRetry
) {
}

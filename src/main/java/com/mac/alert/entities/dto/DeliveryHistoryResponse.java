package com.mac.alert.entities.dto;

import java.time.Instant;
import java.util.UUID;

public record DeliveryHistoryResponse(
        UUID id,
        UUID alertId,
        String sourceSystem,
        String subject,
        String recipients,
        int attemptNo,
        String triggerSource,
        String result,
        String failureCategory,
        Boolean retryable,
        String errorCode,
        String errorMessage,
        String providerMessageId,
        Instant startedAt,
        Instant completedAt,
        long durationMs,
        Instant nextRetryAt) {}

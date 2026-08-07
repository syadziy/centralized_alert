package com.mac.alert.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mac.alert.entities.constant.TriggerSource;
import com.mac.alert.entities.model.AlertMessage;
import com.mac.alert.entities.model.ClaimedAlert;
import com.mac.alert.entities.model.CreateAlert;
import com.mac.alert.entities.model.DeliveryFailure;
import com.mac.alert.entities.model.ExistingAlert;

public interface AlertRepository {

    List<ClaimedAlert> claimPendingAlerts(
            int batchSize,
            Duration processingTimeout,
            String workerId);

    Optional<ClaimedAlert> claimById(
            UUID alertId,
            Duration processingTimeout,
            String workerId);

    AlertMessage findMessageById(UUID alertId);

    void markSuccess(
            UUID alertId,
            int attemptNo,
            TriggerSource triggerSource,
            String providerMessageId,
            String workerId,
            Instant startedAt,
            Instant completedAt);

    void markFailure(
            UUID alertId,
            int attemptNo,
            TriggerSource triggerSource,
            DeliveryFailure failure,
            String targetStatus,
            Instant nextRetryAt,
            String workerId,
            Instant startedAt,
            Instant completedAt);

    boolean insertAlertRequest(
            UUID alertId,
            CreateAlert model,
            Instant createdAt
    );

    void insertRecipients(
            UUID alertId,
            List<CreateAlert.Recipient> recipients,
            Instant createdAt
    );

    void insertAttachments(
            UUID alertId,
            List<CreateAlert.Attachment> attachments,
            Instant createdAt
    );

    ExistingAlert findExistingAlert(
            String sourceSystem,
            String idempotencyKey
    );
}

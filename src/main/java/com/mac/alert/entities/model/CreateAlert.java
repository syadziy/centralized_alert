package com.mac.alert.entities.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.mac.alert.entities.constant.AlertBodyType;
import com.mac.alert.entities.constant.AlertCreatedSource;
import com.mac.alert.entities.constant.AttachmentDisposition;
import com.mac.alert.entities.constant.RecipientType;
import com.mac.alert.entities.constant.StorageType;

public record CreateAlert(
        String sourceSystem,
        String idempotencyKey,
        String correlationId,
        AlertCreatedSource createdSource,
        String senderEmail,
        String senderName,
        String replyToEmail,
        String subject,
        String body,
        AlertBodyType bodyType,
        Map<String, Object> templateVariables,
        int priority,
        Instant scheduledAt,
        int maxRetry,
        List<Recipient> recipients,
        List<Attachment> attachments
) {

    public CreateAlert withRecipients(List<Recipient> effectiveRecipients) {
        return new CreateAlert(sourceSystem, idempotencyKey, correlationId, createdSource,
                senderEmail, senderName, replyToEmail, subject, body, bodyType, templateVariables,
                priority, scheduledAt, maxRetry, List.copyOf(effectiveRecipients), attachments);
    }

    public record Recipient(
            RecipientType type,
            String email,
            String displayName
    ) {
    }

    public record Attachment(
            String fileName,
            String contentType,
            long fileSizeBytes,
            StorageType storageType,
            String storageKey,
            String checksumSha256,
            AttachmentDisposition disposition,
            String contentId
    ) {
    }
}

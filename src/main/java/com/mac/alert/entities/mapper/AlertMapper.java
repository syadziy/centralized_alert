package com.mac.alert.entities.mapper;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mac.alert.config.properties.AlertCreateProperties;
import com.mac.alert.entities.constant.AlertCreatedSource;
import com.mac.alert.entities.dto.AlertAttachmentRequest;
import com.mac.alert.entities.dto.AlertRecipientRequest;
import com.mac.alert.entities.dto.CreateAlertRequest;
import com.mac.alert.entities.model.CreateAlert;

import org.springframework.stereotype.Component;

@Component
public class AlertMapper {

    private final AlertCreateProperties properties;

    public AlertMapper(
            AlertCreateProperties properties
    ) {
        this.properties = properties;
    }

    public CreateAlert toCommand(
            CreateAlertRequest request,
            AlertCreatedSource createdSource,
            Instant currentTime
    ) {
        int priority = request.priority() == null
                ? properties.defaultPriority()
                : request.priority();

        Instant scheduledAt = request.scheduledAt() == null
                ? currentTime
                : request.scheduledAt();

        Map<String, Object> variables =
                request.templateVariables() == null
                        ? Map.of()
                        : Map.copyOf(request.templateVariables());

        List<CreateAlert.Recipient> recipients =
                request.recipients()
                        .stream()
                        .map(this::toRecipient)
                        .toList();

        List<CreateAlert.Attachment> attachments =
                request.attachments() == null
                        ? List.of()
                        : request.attachments()
                                .stream()
                                .map(this::toAttachment)
                                .toList();

        return new CreateAlert(
                request.sourceSystem().trim(),
                request.idempotencyKey().trim(),
                trimToNull(request.correlationId()),
                createdSource,
                request.senderEmail()
                        .trim()
                        .toLowerCase(Locale.ROOT),
                trimToNull(request.senderName()),
                normalizeEmail(request.replyToEmail()),
                request.subject().trim(),
                request.body(),
                request.bodyType(),
                variables,
                priority,
                scheduledAt,
                properties.defaultMaxRetry(),
                recipients,
                attachments
        );
    }

    private CreateAlert.Recipient toRecipient(
            AlertRecipientRequest recipient
    ) {
        return new CreateAlert.Recipient(
                recipient.type(),
                normalizeEmail(recipient.email()),
                trimToNull(recipient.displayName())
        );
    }

    private CreateAlert.Attachment toAttachment(
            AlertAttachmentRequest attachment
    ) {
        return new CreateAlert.Attachment(
                attachment.fileName().trim(),
                attachment.contentType().trim(),
                attachment.fileSizeBytes(),
                attachment.storageType(),
                attachment.storageKey().trim(),
                trimToNull(attachment.checksumSha256()),
                attachment.disposition(),
                trimToNull(attachment.contentId())
        );
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
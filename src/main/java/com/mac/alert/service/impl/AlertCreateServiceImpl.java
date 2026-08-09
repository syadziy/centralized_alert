package com.mac.alert.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mac.alert.entities.constant.AttachmentDisposition;
import com.mac.alert.entities.constant.RecipientType;
import com.mac.alert.entities.dto.AlertWebNotification;
import com.mac.alert.entities.model.AlertCreateResult;
import com.mac.alert.entities.model.CreateAlert;
import com.mac.alert.repository.AlertRepository;
import com.mac.alert.service.AlertCreateService;
import com.mac.alert.service.RecipientConfigurationService;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertCreateServiceImpl
        implements AlertCreateService {

    private final AlertRepository alertRepository;
    private final RecipientConfigurationService recipientConfigurationService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public AlertCreateServiceImpl(
            AlertRepository alertRepository,
            RecipientConfigurationService recipientConfigurationService,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.alertRepository = alertRepository;
        this.recipientConfigurationService = recipientConfigurationService;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AlertCreateResult create(
            CreateAlert model
    ) {
        model = model.withRecipients(recipientConfigurationService.resolve(
                model.sourceSystem(), model.recipients()));
        validateBusinessRules(model);

        Instant now = clock.instant();
        UUID newAlertId = UUID.randomUUID();

        boolean inserted =
                alertRepository.insertAlertRequest(
                        newAlertId,
                        model,
                        now
                );

        /*
         * Idempotency key sudah pernah diproses.
         * Jangan memasukkan recipient dan attachment lagi.
         */
        if (!inserted) {
            var existing =
                    alertRepository.findExistingAlert(
                            model.sourceSystem(),
                            model.idempotencyKey()
                    );

            return new AlertCreateResult(
                    existing.alertId(),
                    existing.status(),
                    false,
                    existing.createdAt()
            );
        }

        alertRepository.insertRecipients(
                newAlertId,
                model.recipients(),
                now
        );

        alertRepository.insertAttachments(
                newAlertId,
                model.attachments(),
                now
        );

        eventPublisher.publishEvent(new AlertWebNotification(
                newAlertId,
                "ALERT_CREATED",
                model.sourceSystem(),
                model.subject(),
                model.priority(),
                "PENDING",
                now));

        return new AlertCreateResult(
                newAlertId,
                "PENDING",
                true,
                now
        );
    }

    private void validateBusinessRules(
            CreateAlert model
    ) {
        boolean hasToRecipient =
                model.recipients()
                        .stream()
                        .anyMatch(
                            recipient ->
                                recipient.type()
                                    == RecipientType.TO
                        );

        if (!hasToRecipient) {
            throw new IllegalArgumentException(
                    "Alert must have at least one TO recipient"
            );
        }

        Set<String> uniqueRecipients = new HashSet<>();

        for (var recipient : model.recipients()) {
            String key = recipient.type()
                    + ":"
                    + recipient.email();

            if (!uniqueRecipients.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate recipient with key: " + key
                );
            }
        }

        for (var attachment : model.attachments()) {
            if (attachment.disposition()
                    == AttachmentDisposition.INLINE
                    && (
                        attachment.contentId() == null
                        || attachment.contentId().isBlank()
                    )) {

                throw new IllegalArgumentException(
                        "contentId is required for inline attachment: "
                                + attachment.fileName()
                );
            }
        }
    }
}

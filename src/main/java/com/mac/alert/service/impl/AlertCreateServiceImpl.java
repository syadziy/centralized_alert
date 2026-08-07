package com.mac.alert.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mac.alert.entities.constant.AttachmentDisposition;
import com.mac.alert.entities.constant.RecipientType;
import com.mac.alert.entities.model.AlertCreateResult;
import com.mac.alert.entities.model.CreateAlert;
import com.mac.alert.repository.AlertRepository;
import com.mac.alert.service.AlertCreateService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertCreateServiceImpl
        implements AlertCreateService {

    private final AlertRepository alertRepository;
    private final Clock clock;

    public AlertCreateServiceImpl(
            AlertRepository alertRepository,
            Clock clock
    ) {
        this.alertRepository = alertRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AlertCreateResult create(
            CreateAlert model
    ) {
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
                    "Alert must have minimum 1 recipient TO"
            );
        }

        Set<String> uniqueRecipients = new HashSet<>();

        for (var recipient : model.recipients()) {
            String key = recipient.type()
                    + ":"
                    + recipient.email();

            if (!uniqueRecipients.add(key)) {
                throw new IllegalArgumentException(
                        "Recipient duplicate with key: " + key
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
                        "contentId must be inline attachment: "
                                + attachment.fileName()
                );
            }
        }
    }
}
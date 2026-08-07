package com.mac.alert.entities.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mac.alert.entities.constant.AlertBodyType;

public record AlertMessage(
        UUID id,
        String senderEmail,
        String senderName,
        String replyToEmail,
        String subject,
        String body,
        AlertBodyType bodyType,
        Map<String, Object> templateVariables,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        List<AlertAttachment> attachments
) {

    public AlertMessage {
        templateVariables = templateVariables == null
                ? Map.of()
                : Map.copyOf(templateVariables);

        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        bcc = bcc == null ? List.of() : List.copyOf(bcc);

        attachments = attachments == null
                ? List.of()
                : List.copyOf(attachments);

        if (to.isEmpty()) {
            throw new IllegalArgumentException(
                    "Alert must have at least one TO recipient"
            );
        }
    }
}

package com.mac.alert.entities.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.mac.alert.entities.constant.AlertBodyType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAlertRequest(

        @NotBlank
        @Size(max = 100)
        String sourceSystem,

        @NotBlank
        @Size(max = 150)
        String idempotencyKey,

        @Size(max = 150)
        String correlationId,

        @NotBlank
        @Email
        @Size(max = 320)
        String senderEmail,

        @Size(max = 150)
        String senderName,

        @Email
        @Size(max = 320)
        String replyToEmail,

        @NotBlank
        @Size(max = 500)
        String subject,

        @NotBlank
        String body,

        @NotNull
        AlertBodyType bodyType,

        Map<String, Object> templateVariables,

        @Min(1)
        @Max(9)
        Integer priority,

        Instant scheduledAt,

        @NotEmpty
        List<@Valid AlertRecipientRequest> recipients,

        List<@Valid AlertAttachmentRequest> attachments
) {
}
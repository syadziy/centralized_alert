package com.mac.alert.entities.model;

import java.util.Map;
import java.util.UUID;

import com.mac.alert.entities.constant.AlertBodyType;

public record AlertHeader(
        UUID id,
        String senderEmail,
        String senderName,
        String replyToEmail,
        String subject,
        String body,
        AlertBodyType bodyType,
        Map<String, Object> templateVariables) {
}

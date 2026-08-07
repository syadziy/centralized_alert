package com.mac.alert.entities.model;

import java.util.UUID;

import com.mac.alert.entities.constant.AttachmentDisposition;
import com.mac.alert.entities.constant.StorageType;

public record AlertAttachment(
        UUID id,
        String fileName,
        String contentType,
        long fileSizeBytes,
        StorageType storageType,
        String storageKey,
        String checksumSha256,
        AttachmentDisposition disposition,
        String contentId
) {

    public AlertAttachment {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Attachment ID must not be null"
            );
        }

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "Attachment filename must not be blank"
            );
        }

        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Attachment storage key must not be blank"
            );
        }

        if (fileSizeBytes < 0) {
            throw new IllegalArgumentException(
                    "Attachment size must not be negative"
            );
        }
    }
}

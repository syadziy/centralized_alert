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
                    "Attachment ID tidak boleh null"
            );
        }

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "Attachment filename tidak boleh kosong"
            );
        }

        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Attachment storage key tidak boleh kosong"
            );
        }

        if (fileSizeBytes < 0) {
            throw new IllegalArgumentException(
                    "Attachment size tidak boleh negatif"
            );
        }
    }
}

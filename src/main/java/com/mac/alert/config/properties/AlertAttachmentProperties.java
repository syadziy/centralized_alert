package com.mac.alert.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "alert.attachment")
public record AlertAttachmentProperties(
        DataSize maxFileSize,
        DataSize maxTotalSize,
        Local local) {

    public AlertAttachmentProperties {
        if (maxFileSize == null
                || maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException(
                    "alert.attachment.max-file-size harus lebih dari 0");
        }

        if (maxTotalSize == null
                || maxTotalSize.toBytes() <= 0) {
            throw new IllegalArgumentException(
                    "alert.attachment.max-total-size harus lebih dari 0");
        }

        if (maxTotalSize.toBytes() < maxFileSize.toBytes()) {
            throw new IllegalArgumentException(
                    "max-total-size tidak boleh lebih kecil dari max-file-size");
        }

        if (local == null
                || local.baseDirectory() == null
                || local.baseDirectory().isBlank()) {
            throw new IllegalArgumentException(
                    "alert.attachment.local.base-directory wajib diisi");
        }
    }

    public record Local(
            String baseDirectory) {
    }
}
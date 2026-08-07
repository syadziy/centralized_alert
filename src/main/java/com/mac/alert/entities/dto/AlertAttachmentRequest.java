package com.mac.alert.entities.dto;

import com.mac.alert.entities.constant.AttachmentDisposition;
import com.mac.alert.entities.constant.StorageType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AlertAttachmentRequest(

        @NotBlank
        @Size(max = 255)
        String fileName,

        @NotBlank
        @Size(max = 150)
        String contentType,

        @PositiveOrZero
        long fileSizeBytes,

        @NotNull
        StorageType storageType,

        @NotBlank
        String storageKey,

        @Pattern(
            regexp = "^[a-fA-F0-9]{64}$",
            message = "checksumSha256 must be a hexadecimal SHA-256 value"
        )
        String checksumSha256,

        @NotNull
        AttachmentDisposition disposition,

        @Size(max = 255)
        String contentId
) {
}

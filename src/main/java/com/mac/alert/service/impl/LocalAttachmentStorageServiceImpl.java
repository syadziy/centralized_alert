package com.mac.alert.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.mac.alert.config.properties.AlertAttachmentProperties;
import com.mac.alert.entities.constant.AlertErrorCode;
import com.mac.alert.entities.constant.StorageType;
import com.mac.alert.entities.model.AlertAttachment;
import com.mac.alert.service.AttachmentStorageService;
import com.mac.alert.utils.exception.AlertDeliveryException;

import org.springframework.stereotype.Service;

@Service
public class LocalAttachmentStorageServiceImpl
        implements AttachmentStorageService {

    private final Path baseDirectory;
    private final AlertAttachmentProperties properties;

    public LocalAttachmentStorageServiceImpl(
            AlertAttachmentProperties properties
    ) {
        this.properties = properties;

        this.baseDirectory = Path.of(
                        properties.local().baseDirectory()
                )
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public byte[] load(AlertAttachment attachment) {
        validateStorageType(attachment);

        Path filePath = resolveSafePath(attachment);

        validateMetadataSize(attachment);
        validateFileExists(attachment, filePath);

        byte[] content = readFile(attachment, filePath);

        validateActualSize(attachment, content);
        validateChecksum(attachment, content);

        return content;
    }

    private void validateStorageType(
            AlertAttachment attachment
    ) {
        if (attachment.storageType() != StorageType.LOCAL) {
            throw new AlertDeliveryException(
                    AlertErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                    "Storage type is not supported by local storage.: "
                            + attachment.storageType()
            );
        }
    }

    private Path resolveSafePath(
            AlertAttachment attachment
    ) {
        Path resolvedPath = baseDirectory
                .resolve(attachment.storageKey())
                .normalize();

        /*
         * Mencegah path traversal:
         * ../../etc/passwd
         * ../secret/private.key
         */
        if (!resolvedPath.startsWith(baseDirectory)) {
            throw new AlertDeliveryException(
                    AlertErrorCode.ATTACHMENT_NOT_FOUND,
                    "Invalid attachment location: "
                            + attachment.fileName()
            );
        }

        return resolvedPath;
    }

    private void validateMetadataSize(
            AlertAttachment attachment
    ) {
        long maximumSize =
                properties.maxFileSize().toBytes();

        if (attachment.fileSizeBytes() > maximumSize) {
            throw new AlertDeliveryException(
                    AlertErrorCode.ATTACHMENT_TOO_LARGE,
                    "The attachment exceeds the size limit. "
                            + "fileName="
                            + attachment.fileName()
                            + ", size="
                            + attachment.fileSizeBytes()
                            + ", maximum="
                            + maximumSize
            );
        }
    }

    private void validateFileExists(
            AlertAttachment attachment,
            Path filePath
    ) {
        if (!Files.exists(filePath)
                || !Files.isRegularFile(filePath)) {
            throw new AlertDeliveryException(
                    AlertErrorCode.ATTACHMENT_NOT_FOUND,
                    "Attachment not found: "
                            + attachment.fileName()
            );
        }
    }

    private byte[] readFile(
            AlertAttachment attachment,
            Path filePath
    ) {
        try {
            return Files.readAllBytes(filePath);

        } catch (IOException exception) {
            throw new AlertDeliveryException(
                    AlertErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                    "Failed to read attachment: "
                            + attachment.fileName(),
                    exception
            );
        }
    }

    private void validateActualSize(
            AlertAttachment attachment,
            byte[] content
    ) {
        long maximumSize =
                properties.maxFileSize().toBytes();

        /*
         * Validasi actual size tetap diperlukan.
         * Metadata database tidak boleh dipercaya sepenuhnya.
         */
        if (content.length > maximumSize) {
            throw new AlertDeliveryException(
                    AlertErrorCode.ATTACHMENT_TOO_LARGE,
                    "The actual size of the attachment exceeds the limit. "
                            + "fileName="
                            + attachment.fileName()
                            + ", actualSize="
                            + content.length
                            + ", maximum="
                            + maximumSize
            );
        }

        /*
         * Perbedaan size metadata dan file aktual
         * mengindikasikan file berubah atau metadata salah.
         */
        if (content.length != attachment.fileSizeBytes()) {
            throw new AlertDeliveryException(
                    AlertErrorCode.ATTACHMENT_CHECKSUM_MISMATCH,
                    "The actual size of the attachment differs from the metadata. "
                            + "fileName="
                            + attachment.fileName()
                            + ", expected="
                            + attachment.fileSizeBytes()
                            + ", actual="
                            + content.length
            );
        }
    }

    private void validateChecksum(
            AlertAttachment attachment,
            byte[] content
    ) {
        String expectedChecksum =
                attachment.checksumSha256();

        if (expectedChecksum == null
                || expectedChecksum.isBlank()) {
            return;
        }

        String actualChecksum =
                calculateSha256(content);

        if (!actualChecksum.equalsIgnoreCase(
                expectedChecksum
        )) {
            throw new AlertDeliveryException(
                    AlertErrorCode.ATTACHMENT_CHECKSUM_MISMATCH,
                    "The attachment checksum does not match. "
                            + "fileName="
                            + attachment.fileName()
            );
        }
    }

    private String calculateSha256(byte[] content) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(content);

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            /*
             * SHA-256 wajib tersedia di Java.
             * Jika tidak tersedia, ini berarti masalah runtime.
             */
            throw new IllegalStateException(
                    "The SHA-256 algorithm is not available.",
                    exception
            );
        }
    }
}
package com.mac.alert.entities.constant;

public enum AlertErrorCode {

    SMTP_CONNECTION_TIMEOUT(
            FailureCategory.TEMPORARY,
            true
    ),

    SMTP_READ_TIMEOUT(
            FailureCategory.TEMPORARY,
            true
    ),

    SMTP_AUTHENTICATION_FAILED(
            FailureCategory.PERMANENT,
            false
    ),

    SMTP_CONNECTION_REFUSED(
            FailureCategory.TEMPORARY,
            true
    ),

    SMTP_4XX_TEMPORARY_FAILURE(
            FailureCategory.TEMPORARY,
            true
    ),

    SMTP_5XX_PERMANENT_FAILURE(
            FailureCategory.PERMANENT,
            false
    ),

    INVALID_RECIPIENT(
            FailureCategory.PERMANENT,
            false
    ),

    INVALID_SENDER(
            FailureCategory.PERMANENT,
            false
    ),

    ATTACHMENT_NOT_FOUND(
            FailureCategory.PERMANENT,
            false
    ),

    ATTACHMENT_TOO_LARGE(
            FailureCategory.PERMANENT,
            false
    ),

    ATTACHMENT_DOWNLOAD_FAILED(
            FailureCategory.TEMPORARY,
            true
    ),

    ATTACHMENT_CHECKSUM_MISMATCH(
            FailureCategory.PERMANENT,
            false
    ),

    TEMPLATE_RENDER_FAILED(
            FailureCategory.PERMANENT,
            false
    ),

    EMAIL_BUILD_FAILED(
            FailureCategory.PERMANENT,
            false
    ),

    DATABASE_ERROR(
            FailureCategory.TEMPORARY,
            true
    ),

    UNKNOWN_ERROR(
            FailureCategory.UNKNOWN,
            true
    );

    private final FailureCategory category;
    private final boolean retryable;

    AlertErrorCode(
            FailureCategory category,
            boolean retryable
    ) {
        this.category = category;
        this.retryable = retryable;
    }

    public FailureCategory getCategory() {
        return category;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
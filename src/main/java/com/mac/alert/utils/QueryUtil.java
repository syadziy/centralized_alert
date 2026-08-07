package com.mac.alert.utils;

public class QueryUtil {

    public static final String CLAIM_PENDING_SQL = """
            WITH candidates AS (
                SELECT id
                FROM alert_request
                WHERE status IN ('PENDING', 'RETRY')
                  AND scheduled_at <= CURRENT_TIMESTAMP
                  AND (
                      next_retry_at IS NULL
                      OR next_retry_at <= CURRENT_TIMESTAMP
                  )
                ORDER BY
                    priority ASC,
                    scheduled_at ASC,
                    created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE alert_request alert
            SET
                status = 'PROCESSING',
                locked_by = :workerId,
                locked_at = CURRENT_TIMESTAMP,
                processing_started_at = CURRENT_TIMESTAMP,
                processing_expires_at =
                    CURRENT_TIMESTAMP
                    + (:timeoutSeconds * INTERVAL '1 second'),
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            FROM candidates
            WHERE alert.id = candidates.id
            RETURNING
                alert.id,
                alert.retry_count + 1 AS attempt_no,
                alert.max_retry
            """;

    public static final String CLAIM_BY_ID_SQL = """
            UPDATE alert_request
            SET
                status = 'PROCESSING',
                locked_by = :workerId,
                locked_at = CURRENT_TIMESTAMP,
                processing_started_at = CURRENT_TIMESTAMP,
                processing_expires_at =
                    CURRENT_TIMESTAMP
                    + (:timeoutSeconds * INTERVAL '1 second'),
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = :alertId
              AND status IN ('PENDING', 'RETRY')
            RETURNING
                id,
                retry_count + 1 AS attempt_no,
                max_retry
            """;

    public static final String FIND_ALERT_SQL = """
            SELECT
                id,
                sender_email,
                sender_name,
                reply_to_email,
                subject,
                body,
                body_type,
                template_variables
            FROM alert_request
            WHERE id = ?
            AND status = 'PROCESSING'
            """;

    public static final String FIND_RECIPIENTS_SQL = """
            SELECT recipient_type, email
            FROM alert_recipient
            WHERE alert_id = ?
            ORDER BY created_at ASC
            """;

    public static final String FIND_ATTACHMENTS_SQL = """
            SELECT
                id,
                file_name,
                content_type,
                file_size_bytes,
                storage_type,
                storage_key,
                checksum_sha256,
                disposition,
                content_id
            FROM alert_attachment
            WHERE alert_id = ?
            ORDER BY created_at ASC
            """;

    public static final String UPDATE_SUCCESS_SQL = """
            UPDATE alert_request
            SET
                status = 'SENT',
                sent_at = :completedAt,
                provider_message_id = :providerMessageId,
                locked_by = NULL,
                locked_at = NULL,
                processing_started_at = NULL,
                processing_expires_at = NULL,
                next_retry_at = NULL,
                last_error_code = NULL,
                last_error_message = NULL,
                updated_at = :completedAt,
                version = version + 1
            WHERE id = :alertId
              AND status = 'PROCESSING'
              AND locked_by = :workerId
            """;

    public static final String INSERT_SUCCESS_HISTORY_SQL = """
            INSERT INTO alert_delivery_history (
                alert_id,
                attempt_no,
                trigger_source,
                result,
                provider_message_id,
                worker_id,
                started_at,
                completed_at,
                duration_ms
            )
            VALUES (
                :alertId,
                :attemptNo,
                :triggerSource,
                'SUCCESS',
                :providerMessageId,
                :workerId,
                :startedAt,
                :completedAt,
                :durationMs
            )
            """;

    public static final String UPDATE_FAILURE_SQL = """
            UPDATE alert_request
            SET
                status = :targetStatus,
                retry_count =
                    CASE
                        WHEN :targetStatus = 'RETRY'
                        THEN retry_count + 1
                        ELSE retry_count
                    END,
                next_retry_at = :nextRetryAt,
                locked_by = NULL,
                locked_at = NULL,
                processing_started_at = NULL,
                processing_expires_at = NULL,
                last_error_code = :errorCode,
                last_error_message = :errorMessage,
                updated_at = :completedAt,
                version = version + 1
            WHERE id = :alertId
              AND status = 'PROCESSING'
              AND locked_by = :workerId
            """;

    public static final String INSERT_FAILURE_HISTORY_SQL = """
            INSERT INTO alert_delivery_history (
                alert_id,
                attempt_no,
                trigger_source,
                result,
                failure_category,
                retryable,
                error_code,
                error_message,
                exception_class,
                provider_response_code,
                worker_id,
                started_at,
                completed_at,
                duration_ms,
                next_retry_at
            )
            VALUES (
                :alertId,
                :attemptNo,
                :triggerSource,
                'FAILED',
                :failureCategory,
                :retryable,
                :errorCode,
                :errorMessage,
                :exceptionClass,
                :providerResponseCode,
                :workerId,
                :startedAt,
                :completedAt,
                :durationMs,
                :nextRetryAt
            )
            """;

    public static final String INSERT_ALERT_REQUEST_SQL = """
        INSERT INTO alert_request (
            id,
            source_system,
            idempotency_key,
            correlation_id,
            channel,
            created_source,
            sender_email,
            sender_name,
            reply_to_email,
            subject,
            body,
            body_type,
            template_variables,
            priority,
            status,
            scheduled_at,
            retry_count,
            max_retry,
            created_at,
            updated_at,
            version
        )
        VALUES (
            :id,
            :sourceSystem,
            :idempotencyKey,
            :correlationId,
            'EMAIL',
            :createdSource,
            :senderEmail,
            :senderName,
            :replyToEmail,
            :subject,
            :body,
            :bodyType,
            CAST(:templateVariables AS JSONB),
            :priority,
            'PENDING',
            :scheduledAt,
            0,
            :maxRetry,
            :createdAt,
            :createdAt,
            0
        )
        ON CONFLICT (
            source_system,
            idempotency_key
        )
        DO NOTHING
        RETURNING id
        """;

    public static final String INSERT_RECIPIENT_SQL = """
        INSERT INTO alert_recipient (
            id,
            alert_id,
            recipient_type,
            email,
            display_name,
            created_at
        )
        VALUES (
            :id,
            :alertId,
            :recipientType,
            :email,
            :displayName,
            :createdAt
        )
        """;

    public static final String INSERT_ATTACHMENT_SQL = """
        INSERT INTO alert_attachment (
            id,
            alert_id,
            file_name,
            content_type,
            file_size_bytes,
            storage_type,
            storage_key,
            checksum_sha256,
            disposition,
            content_id,
            created_at
        )
        VALUES (
            :id,
            :alertId,
            :fileName,
            :contentType,
            :fileSizeBytes,
            :storageType,
            :storageKey,
            :checksumSha256,
            :disposition,
            :contentId,
            :createdAt
        )
        """;

    public static final String FIND_EXISTING_ALERT_SQL = """
        SELECT
            id,
            status,
            created_at
        FROM alert_request
        WHERE source_system = :sourceSystem
        AND idempotency_key = :idempotencyKey
        """;
}

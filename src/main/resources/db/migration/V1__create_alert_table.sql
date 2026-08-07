-- =========================================================
-- 1. ALERT REQUEST
-- Menyimpan data utama dan status terakhir alert.
-- =========================================================
CREATE TABLE alert_request (
    id                      UUID            NOT NULL
        DEFAULT gen_random_uuid(),

    source_system           VARCHAR(100)    NOT NULL,
    idempotency_key         VARCHAR(150)    NOT NULL,
    correlation_id          VARCHAR(150),

    channel                 VARCHAR(20)     NOT NULL
        DEFAULT 'EMAIL',

    created_source          VARCHAR(20)     NOT NULL,

    sender_email            VARCHAR(320)    NOT NULL,
    sender_name             VARCHAR(150),
    reply_to_email          VARCHAR(320),

    subject                 VARCHAR(500)    NOT NULL,
    body                    TEXT            NOT NULL,
    body_type               VARCHAR(10)     NOT NULL
        DEFAULT 'HTML',

    priority                SMALLINT        NOT NULL
        DEFAULT 5,

    status                  VARCHAR(20)     NOT NULL
        DEFAULT 'PENDING',

    scheduled_at            TIMESTAMPTZ     NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    retry_count             INTEGER         NOT NULL
        DEFAULT 0,

    max_retry               INTEGER         NOT NULL
        DEFAULT 5,

    next_retry_at           TIMESTAMPTZ,

    locked_by               VARCHAR(150),
    locked_at               TIMESTAMPTZ,

    processing_started_at   TIMESTAMPTZ,
    processing_expires_at   TIMESTAMPTZ,

    sent_at                 TIMESTAMPTZ,

    provider_message_id     VARCHAR(255),

    last_error_code         VARCHAR(100),
    last_error_message      VARCHAR(2000),

    created_by              VARCHAR(150),

    created_at              TIMESTAMPTZ     NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at              TIMESTAMPTZ     NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    version                 BIGINT          NOT NULL
        DEFAULT 0,

    CONSTRAINT pk_alert_request
        PRIMARY KEY (id),

    CONSTRAINT uq_alert_request_idempotency
        UNIQUE (source_system, idempotency_key),

    CONSTRAINT chk_alert_request_channel
        CHECK (
            channel IN ('EMAIL')
        ),

    CONSTRAINT chk_alert_request_created_source
        CHECK (
            created_source IN (
                'API',
                'KAFKA',
                'SYSTEM'
            )
        ),

    CONSTRAINT chk_alert_request_body_type
        CHECK (
            body_type IN ('TEXT', 'HTML')
        ),

    CONSTRAINT chk_alert_request_priority
        CHECK (
            priority BETWEEN 1 AND 9
        ),

    CONSTRAINT chk_alert_request_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'RETRY',
                'SENT',
                'FAILED',
                'DEAD',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_alert_request_retry
        CHECK (
            retry_count >= 0
            AND max_retry >= 0
            AND retry_count <= max_retry
        )
);

COMMENT ON COLUMN alert_request.priority IS
    '1 adalah prioritas tertinggi dan 9 adalah prioritas terendah';

COMMENT ON COLUMN alert_request.idempotency_key IS
    'Mencegah request yang sama dibuat lebih dari satu kali';

COMMENT ON COLUMN alert_request.version IS
    'Digunakan untuk optimistic locking';

-- =========================================================
-- 2. ALERT RECIPIENT
-- Menyimpan TO, CC, dan BCC secara terpisah.
-- =========================================================
CREATE TABLE alert_recipient (
    id                  UUID            NOT NULL
        DEFAULT gen_random_uuid(),

    alert_id            UUID            NOT NULL,

    recipient_type      VARCHAR(10)     NOT NULL,

    email               VARCHAR(320)    NOT NULL,

    display_name        VARCHAR(150),

    created_at          TIMESTAMPTZ     NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_alert_recipient
        PRIMARY KEY (id),

    CONSTRAINT fk_alert_recipient_alert
        FOREIGN KEY (alert_id)
        REFERENCES alert_request (id)
        ON DELETE CASCADE,

    CONSTRAINT uq_alert_recipient
        UNIQUE (
            alert_id,
            recipient_type,
            email
        ),

    CONSTRAINT chk_alert_recipient_type
        CHECK (
            recipient_type IN ('TO', 'CC', 'BCC')
        )
);

-- =========================================================
-- 3. ALERT ATTACHMENT
-- Menyimpan metadata attachment, bukan file besar secara langsung.
-- =========================================================
CREATE TABLE alert_attachment (
    id                  UUID            NOT NULL
        DEFAULT gen_random_uuid(),

    alert_id            UUID            NOT NULL,

    file_name           VARCHAR(255)    NOT NULL,

    content_type        VARCHAR(150)    NOT NULL,

    file_size_bytes     BIGINT          NOT NULL,

    storage_type        VARCHAR(20)     NOT NULL,

    storage_key         TEXT            NOT NULL,

    checksum_sha256     CHAR(64),

    disposition         VARCHAR(20)     NOT NULL
        DEFAULT 'ATTACHMENT',

    content_id          VARCHAR(255),

    created_at          TIMESTAMPTZ     NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_alert_attachment
        PRIMARY KEY (id),

    CONSTRAINT fk_alert_attachment_alert
        FOREIGN KEY (alert_id)
        REFERENCES alert_request (id)
        ON DELETE CASCADE,

    CONSTRAINT chk_alert_attachment_size
        CHECK (
            file_size_bytes >= 0
        ),

    CONSTRAINT chk_alert_attachment_storage_type
        CHECK (
            storage_type IN (
                'LOCAL',
                'S3',
                'MINIO'
            )
        ),

    CONSTRAINT chk_alert_attachment_disposition
        CHECK (
            disposition IN (
                'ATTACHMENT',
                'INLINE'
            )
        )
);

-- =========================================================
-- 4. ALERT DELIVERY HISTORY
-- Satu row mewakili satu percobaan pengiriman.
-- History tidak boleh di-update atau dihapus oleh proses normal.
-- =========================================================
CREATE TABLE alert_delivery_history (
    id                          UUID            NOT NULL
        DEFAULT gen_random_uuid(),

    alert_id                    UUID            NOT NULL,

    attempt_no                  INTEGER         NOT NULL,

    trigger_source              VARCHAR(20)     NOT NULL,

    result                      VARCHAR(20)     NOT NULL,

    failure_category            VARCHAR(20),

    retryable                   BOOLEAN,

    error_code                  VARCHAR(100),

    error_message               VARCHAR(2000),

    exception_class             VARCHAR(255),

    provider_response_code      VARCHAR(100),

    provider_response_message   TEXT,

    provider_message_id         VARCHAR(255),

    worker_id                   VARCHAR(150),

    started_at                  TIMESTAMPTZ     NOT NULL,

    completed_at                TIMESTAMPTZ     NOT NULL,

    duration_ms                 BIGINT          NOT NULL,

    next_retry_at               TIMESTAMPTZ,

    metadata                    JSONB           NOT NULL
        DEFAULT '{}'::JSONB,

    created_at                  TIMESTAMPTZ     NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_alert_delivery_history
        PRIMARY KEY (id),

    CONSTRAINT fk_alert_delivery_history_alert
        FOREIGN KEY (alert_id)
        REFERENCES alert_request (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_alert_delivery_attempt
        UNIQUE (
            alert_id,
            attempt_no
        ),

    CONSTRAINT chk_alert_delivery_attempt
        CHECK (
            attempt_no > 0
        ),

    CONSTRAINT chk_alert_delivery_trigger_source
        CHECK (
            trigger_source IN (
                'SCHEDULER',
                'API',
                'KAFKA',
                'RECOVERY'
            )
        ),

    CONSTRAINT chk_alert_delivery_result
        CHECK (
            result IN (
                'SUCCESS',
                'FAILED'
            )
        ),

    CONSTRAINT chk_alert_delivery_failure_category
        CHECK (
            failure_category IS NULL
            OR failure_category IN (
                'TEMPORARY',
                'PERMANENT',
                'UNKNOWN'
            )
        ),

    CONSTRAINT chk_alert_delivery_duration
        CHECK (
            duration_ms >= 0
        ),

    CONSTRAINT chk_alert_delivery_result_data
        CHECK (
            (
                result = 'SUCCESS'
                AND failure_category IS NULL
                AND error_code IS NULL
            )
            OR
            (
                result = 'FAILED'
                AND error_code IS NOT NULL
            )
        )
);

COMMENT ON TABLE alert_delivery_history IS
    'Append-only history untuk setiap percobaan pengiriman alert';

COMMENT ON COLUMN alert_delivery_history.failure_category IS
    'TEMPORARY dapat di-retry, PERMANENT tidak perlu di-retry';

COMMENT ON COLUMN alert_delivery_history.metadata IS
    'Metadata teknis tambahan yang tidak mengandung password, token, atau data sensitif';
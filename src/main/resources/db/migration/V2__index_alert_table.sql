-- Untuk scheduler mengambil PENDING atau RETRY.
CREATE INDEX IF NOT EXISTS idx_alert_request_pickup
    ON alert_request (
        priority,
        scheduled_at,
        next_retry_at,
        created_at
    )
    WHERE status IN ('PENDING', 'RETRY');

-- Untuk mencari proses yang terkunci atau timeout.
CREATE INDEX IF NOT EXISTS idx_alert_request_processing_timeout
    ON alert_request (
        processing_expires_at
    )
    WHERE status = 'PROCESSING';

-- Untuk pencarian berdasarkan correlation ID.
CREATE INDEX IF NOT EXISTS idx_alert_request_correlation
    ON alert_request (
        source_system,
        correlation_id
    )
    WHERE correlation_id IS NOT NULL;

-- Untuk halaman monitoring berdasarkan status.
CREATE INDEX IF NOT EXISTS idx_alert_request_status_created
    ON alert_request (
        status,
        created_at DESC
    );

-- Untuk mengambil recipient sebuah alert.
CREATE INDEX IF NOT EXISTS idx_alert_recipient_alert
    ON alert_recipient (alert_id);

-- Untuk mengambil attachment sebuah alert.
CREATE INDEX IF NOT EXISTS idx_alert_attachment_alert
    ON alert_attachment (alert_id);

-- Untuk mengambil history terbaru sebuah alert.
CREATE INDEX IF NOT EXISTS idx_alert_delivery_history_alert
    ON alert_delivery_history (
        alert_id,
        attempt_no DESC
    );

-- Untuk monitoring kegagalan.
CREATE INDEX IF NOT EXISTS idx_alert_delivery_history_failed
    ON alert_delivery_history (
        created_at DESC,
        error_code
    )
    WHERE result = 'FAILED';
CREATE TABLE alert_recipient_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_system VARCHAR(100) NOT NULL DEFAULT '*',
    recipient_type VARCHAR(10) NOT NULL,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(150),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_alert_recipient_configuration UNIQUE (source_system, recipient_type, email),
    CONSTRAINT chk_alert_recipient_configuration_type
        CHECK (recipient_type IN ('TO', 'CC', 'BCC')),
    CONSTRAINT chk_alert_recipient_configuration_source
        CHECK (source_system = '*' OR length(trim(source_system)) > 0)
);

CREATE INDEX idx_alert_recipient_configuration_resolution
    ON alert_recipient_configuration (source_system, enabled, recipient_type, email);

COMMENT ON TABLE alert_recipient_configuration IS
    'Dashboard-managed recipients. Source-specific rows override matching global rows.';

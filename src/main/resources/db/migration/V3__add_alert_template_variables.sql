ALTER TABLE alert_request
ADD COLUMN template_variables JSONB NOT NULL
DEFAULT '{}'::JSONB;
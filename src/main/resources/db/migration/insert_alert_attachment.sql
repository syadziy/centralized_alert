INSERT INTO alert_attachment (
    alert_id,
    file_name,
    content_type,
    file_size_bytes,
    storage_type,
    storage_key,
    checksum_sha256,
    disposition
)
VALUES (
    'ca3f1853-4aac-4727-8205-8c8891107d5a',
    'invoice-10001.pdf',
    'application/pdf',
    125020,
    'LOCAL',
    'invoices/invoice-10001.pdf',
    '7a9f20d4c731d6b93c...',
    'ATTACHMENT'
);
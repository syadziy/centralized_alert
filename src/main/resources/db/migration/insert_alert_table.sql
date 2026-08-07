INSERT INTO alert_request (
    source_system,
    idempotency_key,
    created_source,
    sender_email,
    subject,
    body,
    body_type,
    template_variables,
    priority,
    status,
    scheduled_at
)
VALUES (
    'PAYMENT-SERVICE',
    'PAYMENT-TRX-10001',
    'SYSTEM',
    'no-reply@example.com',
    'Pembayaran berhasil',
    '
        <h1>
            Halo,
            <span th:text="${customerName}"></span>
        </h1>

        <p>
            Transaksi
            <strong th:text="${transactionId}"></strong>
            berhasil diproses.
        </p>

        <p>
            Nominal:
            <span th:text="${amount}"></span>
        </p>
    ',
    'HTML',
    '{
        "customerName": "Arfin",
        "transactionId": "TRX-10001",
        "amount": "Rp150.000"
    }'::JSONB,
    1,
    'PENDING',
    CURRENT_TIMESTAMP
);
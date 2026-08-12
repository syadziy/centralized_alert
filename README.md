# Centralized Alert Service

`centralized_alert` adalah service terpusat untuk menerima, menyimpan, dan mengirim alert email
dari service lain. Alert dapat dibuat melalui REST API atau Kafka, lalu dikirim secara manual,
melalui event Kafka, atau otomatis oleh scheduler internal.

JVM, JDBC session, persisted timestamps, logs, dan API timestamps menggunakan UTC secara default
melalui `APP_TIMEZONE=UTC`.

Service ini dirancang agar proses pengiriman durable, idempotent, aman dijalankan oleh beberapa
instance, dan mudah dipantau melalui ECS structured logging, trace ID, Actuator, serta Prometheus.

## Fitur utama

- Pembuatan alert melalui REST API dan Kafka.
- Idempotency berdasarkan pasangan `sourceSystem` dan `idempotencyKey`.
- Recipient `TO`, `CC`, dan `BCC` dengan validasi email dan duplikasi.
- Konfigurasi recipient global atau per `sourceSystem` yang dapat dikelola dashboard.
- Email body `TEXT` atau `HTML` dengan Thymeleaf template variables.
- Penjadwalan pengiriman menggunakan `scheduledAt`.
- Attachment metadata dengan storage `LOCAL`, `S3`, atau `MINIO`.
- Implementasi pembacaan attachment `LOCAL` dengan proteksi path traversal, ukuran, dan checksum.
- Pengiriman SMTP secara paralel menggunakan Java virtual threads.
- Retry dengan exponential backoff dan klasifikasi error retryable/non-retryable.
- Claim database yang aman untuk beberapa worker dan recovery alert yang timeout.
- Kafka retry dan dead-letter topic (DLT).
- Notifikasi dashboard realtime melalui STOMP over WebSocket setelah transaksi alert berhasil.
- Response envelope, global exception handling, security, OpenAPI, ECS logging, trace ID, dan MDC
  dari `sdk-util`.

## Teknologi

| Komponen | Implementasi |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 4.1.0 |
| REST | Spring MVC + Jakarta Validation |
| Database | PostgreSQL + Spring JDBC |
| Migration | Flyway |
| Messaging | Spring Kafka |
| Realtime | Spring WebSocket + STOMP |
| Email | Jakarta Mail + Thymeleaf |
| Concurrency | Java virtual threads |
| Monitoring | Actuator + Prometheus |
| Shared library | `com.mac:sdk-util:1.0.0` |

## Alur pemrosesan

```text
REST POST /api/v1/alert             Kafka centralized-alert.create
             │                                  │
             └──────────── validate + normalize ┘
                                │
                                ▼
                 Simpan alert secara idempotent
                 beserta recipient dan attachment
                                │
              ┌─────────────────┼──────────────────┐
              │                 │                  │
        pickup scheduler   manual dispatch    Kafka dispatch event
              │                 │                  │
              └──────────── claim alert ───────────┘
                                │
                                ▼
                 Kirim email pada virtual thread
                                │
                 ┌──────────────┴──────────────┐
                 ▼                             ▼
           SENT + history             RETRY/FAILED/DEAD
                                      + delivery history
```

Claim dilakukan dalam transaksi singkat. Operasi SMTP dan pembacaan attachment berlangsung di
luar transaksi database. Alert berstatus `PROCESSING` yang melewati
`alert.processing.processing-timeout` dapat diambil kembali oleh worker lain.

## Prasyarat

- JDK 21
- PostgreSQL
- Kafka
- SMTP server
- Maven Wrapper yang tersedia di repository
- `sdk-util:1.0.0` pada local Maven repository
- OAuth2/JWT issuer jika security diaktifkan

Install versi terbaru sibling SDK sebelum menjalankan service:

```bash
cd ../sdk_util
mvn clean install
cd ../centralized_alert
```

## Menjalankan secara lokal

1. Buat database PostgreSQL:

   ```sql
   CREATE DATABASE centralized_alert;
   ```

2. Salin nilai yang diperlukan dari `.env.example` ke environment lokal. Jangan commit file
   `.env` atau credential sebenarnya.

3. Pastikan PostgreSQL, Kafka, SMTP, dan OAuth2 issuer yang dikonfigurasi dapat diakses.

4. Jalankan aplikasi:

   ```bash
   mvn spring-boot:run
   ```

Port default adalah `9003`. Flyway akan menjalankan migration ketika aplikasi dimulai.

Build dan test:

```bash
mvn clean verify
mvn test
```

`mvn clean verify` menjalankan unit test, membuat laporan JaCoCo di
`target/site/jacoco/index.html`, dan menggagalkan build bila line coverage business production code
kurang dari 90%.

## Docker

Build JAR terlebih dahulu, kemudian jalankan runtime image Java 21. Volume menjaga attachment lokal
tetap tersedia setelah container diganti:

```bash
mvn clean package
docker build -t centralized-alert:1.0.0 .
docker run --rm --env-file .env -p 9003:9003 \
  -v centralized_alert_attachments:/app/data/attachments \
  centralized-alert:1.0.0
```

Isi `.env` dari `.env.example` dan gunakan hostname service Docker untuk PostgreSQL, Kafka, SMTP,
serta issuer OAuth2. Untuk object storage, volume attachment lokal dapat dihilangkan.

Dokumentasi JSON untuk seluruh REST API dan Kafka event tersedia di
`src/main/resources/json/index.json`. File tersebut menjadi indeks menuju contoh request dan
response setiap contract.

## Realtime WebSocket notification

Dashboard terhubung ke endpoint STOMP `ws://localhost:9003/ws/alerts` melalui API Gateway dan
subscribe ke destination `/topic/alerts`. Browser mengirim cookie `ACCESS_TOKEN` yang `HttpOnly`
pada handshake; API Gateway memvalidasi dan me-relay token sehingga JavaScript tidak perlu membaca
JWT. Non-browser client tetap dapat mengirim bearer token pada STOMP frame `CONNECT`, bukan query
string. Kedua alur wajib memiliki permission `alert:read-notifications`.

Notifikasi hanya diterbitkan untuk alert baru setelah transaksi database commit. Request dengan
idempotency key yang sudah tersimpan tidak menghasilkan notifikasi kedua. Payload sengaja tidak
memuat body, recipient, attachment, atau credential. Contoh lengkap tersedia di
`src/main/resources/json/websocket-alert-notification.json`.

## REST API

Semua response REST menggunakan envelope dari `sdk-util`. Client dapat mengirim
`X-Correlation-Id`; jika tidak tersedia, SDK membuat trace ID dan mengembalikannya melalui header
response.

### Mengelola penerima dari dashboard

Dashboard dapat memakai endpoint berikut:

| Method | Endpoint | Permission |
| --- | --- | --- |
| `GET` | `/api/v1/alert/recipients?sourceSystem=PAYMENT-SERVICE&limit=100&offset=0` | `alert:read-recipients` |
| `POST` | `/api/v1/alert/recipients` | `alert:manage-recipients` |
| `PUT` | `/api/v1/alert/recipients/{id}` | `alert:manage-recipients` |
| `DELETE` | `/api/v1/alert/recipients/{id}` | `alert:manage-recipients` |

Contoh membuat penerima khusus `PAYMENT-SERVICE`:

```bash
curl --request POST 'http://localhost:9003/api/v1/alert/recipients' \
  --header 'Authorization: Bearer <access-token>' \
  --header 'Content-Type: application/json' \
  --data '{
    "sourceSystem": "PAYMENT-SERVICE",
    "type": "TO",
    "email": "payment-ops@example.com",
    "displayName": "Payment Operations",
    "enabled": true
  }'
```

Gunakan `sourceSystem: "*"` (atau kosong) untuk konfigurasi global. Saat alert dibuat, konfigurasi
aktif untuk `sourceSystem` tersebut dan konfigurasi global digabung serta dideduplikasi berdasarkan
`type + email`; konfigurasi khusus source menang atas konfigurasi global yang sama, termasuk ketika
konfigurasi khusus dinonaktifkan. Jika minimal satu baris konfigurasi berlaku untuk source tersebut,
seluruh `recipients` pada request alert digantikan oleh penerima aktif hasil konfigurasi. Jika tidak
ada baris konfigurasi global maupun khusus, `recipients` request digunakan sebagai fallback agar
integrasi lama tetap kompatibel. Menonaktifkan seluruh konfigurasi akan membuat alert ditolak karena
tidak memiliki penerima `TO`, bukan kembali ke payload request.
Hasil akhirnya tetap wajib memiliki minimal satu penerima `TO`.

### Membuat alert

`POST /api/v1/alert`

```bash
curl --request POST 'http://localhost:9003/api/v1/alert' \
  --header 'Content-Type: application/json' \
  --header 'X-Correlation-Id: payment-trx-10001' \
  --data '{
    "sourceSystem": "PAYMENT-SERVICE",
    "idempotencyKey": "PAYMENT-SUCCESS-TRX-10001",
    "correlationId": "TRX-10001",
    "senderEmail": "no-reply@example.com",
    "senderName": "Payment Service",
    "replyToEmail": "support@example.com",
    "subject": "Payment completed",
    "body": "<h1>Hello <span th:text=\"${customerName}\"></span></h1>",
    "bodyType": "HTML",
    "templateVariables": {
      "customerName": "Customer"
    },
    "priority": 1,
    "scheduledAt": null,
    "recipients": [
      {
        "type": "TO",
        "email": "customer@example.com",
        "displayName": "Customer"
      }
    ],
    "attachments": []
  }'
```

Aturan penting:

- `sourceSystem` dan `idempotencyKey` wajib dan membentuk idempotency key unik.
- Request pertama menghasilkan HTTP `201`; request duplikat mengembalikan data alert yang sudah
  ada dengan HTTP `200` tanpa membuat recipient atau attachment baru.
- Minimal satu recipient bertipe `TO` wajib tersedia dari konfigurasi dashboard atau request fallback.
- Kombinasi tipe dan email recipient tidak boleh duplikat.
- `priority` berada pada rentang `1`–`9`; `1` adalah prioritas tertinggi.
- Jika `priority` kosong, nilai `alert.create.default-priority` digunakan.
- Jika `scheduledAt` kosong, alert dapat diproses segera.
- Attachment `INLINE` wajib memiliki `contentId`.
- Client-facing validation dan error messages menggunakan bahasa Inggris.

Contoh data response:

```json
{
  "alertId": "97e95252-e1d4-42f4-89aa-c42b61424923",
  "status": "PENDING",
  "created": true,
  "createdAt": "2026-08-09T01:00:00Z",
  "message": "Alert created successfully"
}
```

### Mengirim alert secara manual

`POST /api/v1/alert/{alertId}/dispatch`

```bash
curl --request POST \
  'http://localhost:9003/api/v1/alert/97e95252-e1d4-42f4-89aa-c42b61424923/dispatch'
```

Endpoint menerima alert yang dapat di-claim dari status `PENDING` atau `RETRY`. Jika state alert
tidak memenuhi syarat, response adalah HTTP `409 Conflict`.

## Kafka contract

### Membuat alert melalui Kafka

Topic default: `centralized-alert.create`

```json
{
  "eventId": "3e8bf945-94bf-4a89-a35c-e4f26b22c65e",
  "occurredAt": "2026-08-06T15:30:00Z",
  "data": {
    "sourceSystem": "PAYMENT-SERVICE",
    "idempotencyKey": "PAYMENT-SUCCESS-TRX-10001",
    "correlationId": "TRX-10001",
    "senderEmail": "no-reply@example.com",
    "senderName": "Payment Service",
    "replyToEmail": "support@example.com",
    "subject": "Payment completed",
    "body": "Payment completed successfully",
    "bodyType": "TEXT",
    "templateVariables": {},
    "priority": 1,
    "scheduledAt": "2026-08-06T15:31:00Z",
    "recipients": [
      {
        "type": "TO",
        "email": "customer@example.com",
        "displayName": "Customer"
      }
    ],
    "attachments": []
  }
}
```

Kafka payload divalidasi secara eksplisit. Kafka message key digunakan sebagai `trace.id`; jika
tidak tersedia, service memakai `eventId`, lalu UUID acak sebagai fallback.

### Memicu dispatch melalui Kafka

Topic default: `centralized-alert.requested`

```json
{
  "alertId": "97e95252-e1d4-42f4-89aa-c42b61424923"
}
```

Setelah retry Kafka habis, record dikirim ke topic default
`centralized-alert.requested.dlt`. Nama topic, retry interval, dan jumlah retry dapat dikonfigurasi.

## Attachment

REST/Kafka hanya mengirim metadata attachment; file besar tidak disimpan di payload alert.

```json
{
  "fileName": "invoice-10001.pdf",
  "contentType": "application/pdf",
  "fileSizeBytes": 125020,
  "storageType": "LOCAL",
  "storageKey": "invoices/invoice-10001.pdf",
  "checksumSha256": "b51f8667d26d0f12f73791c2ceff73015a9e6955f58f662217aa23e21cc770b0",
  "disposition": "ATTACHMENT",
  "contentId": null
}
```

Implementasi runtime saat ini membaca storage `LOCAL`. File dicari relatif terhadap
`alert.attachment.local.base-directory`; absolute/path-traversal escape ditolak. Metadata ukuran,
ukuran file aktual, batas maksimum, dan checksum SHA-256 akan diverifikasi sebelum pengiriman.
Enum `S3` dan `MINIO` sudah menjadi bagian kontrak data, tetapi memerlukan implementasi
`AttachmentStorageService` tambahan untuk dapat dikirim.

## Database

Flyway mengelola tabel berikut:

- `alert_request`: lifecycle, idempotency, schedule, retry, dan worker lease.
- `alert_recipient`: snapshot recipient `TO`, `CC`, dan `BCC` untuk setiap alert.
- `alert_recipient_configuration`: konfigurasi penerima global/per source yang dikelola dashboard.
- `alert_attachment`: metadata attachment.
- `alert_delivery_history`: immutable history untuk setiap delivery attempt.

Status alert yang tersimpan adalah `PENDING`, `PROCESSING`, `RETRY`, `SENT`, `FAILED`, `DEAD`, dan
`CANCELLED`. Jangan mengubah migration yang sudah pernah dijalankan; tambahkan migration versi
baru untuk setiap perubahan schema.

## Konfigurasi utama

| Property/environment | Default | Fungsi |
| --- | --- | --- |
| `SERVER_PORT` | `9003` | Port HTTP service |
| `DB_URL` | `jdbc:postgresql://localhost:5432/centralized_alert` | PostgreSQL connection URL |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `ALERT_KAFKA_TOPIC` | `centralized-alert.requested` | Dispatch topic |
| `ALERT_KAFKA_CREATE_TOPIC` | `centralized-alert.create` | Create-alert topic |
| `ALERT_KAFKA_DLT_TOPIC` | `centralized-alert.requested.dlt` | Dead-letter topic |
| `ALERT_PICKUP_ENABLED` | `true` | Mengaktifkan pickup scheduler |
| `ALERT_PICKUP_INTERVAL` | `PT1M` | Interval pickup |
| `ALERT_PICKUP_BATCH_SIZE` | `50` | Maksimum alert per pickup |
| `alert.processing.processing-timeout` | `PT10M` | Lease timeout untuk recovery |
| `alert.processing.max-parallelism` | `10` | Maksimum pengiriman paralel |
| `alert.processing.retry-initial-delay` | `PT1M` | Delay retry awal |
| `alert.processing.retry-max-delay` | `PT30M` | Batas exponential backoff |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` | SMTP server |
| `ATTACHMENT_LOCAL_DIRECTORY` | `./data/attachments` | Root attachment lokal |
| `ALERT_WEBSOCKET_ENABLED` | `true` | Mengaktifkan broker notifikasi realtime |
| `ALERT_WEBSOCKET_ALLOWED_ORIGINS` | `http://localhost:5173` | Origin dashboard yang boleh handshake |
| `OAUTH2_ISSUER_URI` | `http://localhost:9005` | `usermanagement` JWT issuer |

Durasi menggunakan format ISO-8601, misalnya `PT30S`, `PT1M`, dan `PT10M`. Lihat
`.env.example` dan `application.yaml` untuk seluruh property.

## Observability

- Log console dan file menggunakan ECS JSON.
- Business log memakai `event.action`, `event.outcome`, `event.dataset`, dan field `alert.*`.
- REST trace dibaca dari/dikembalikan melalui `X-Correlation-Id`.
- Kafka, scheduler, dan virtual thread membuat atau meneruskan MDC `trace.id` secara eksplisit.
- Health endpoint: `GET /actuator/health`.
- Prometheus endpoint: `GET /actuator/prometheus`.
- Metrics umum: `GET /actuator/metrics`.

Body email, credential, attachment content, access token, dan recipient personal data tidak boleh
ditulis ke log.

## Security

Service dikonfigurasi sebagai OAuth2 resource server. Gunakan bearer token dari `usermanagement`
untuk endpoint yang dilindungi. Untuk production:

Path `/internal/**` tidak memerlukan JWT. Jangan ekspos path ini melalui public ingress; batasi
aksesnya dengan network policy atau service mesh karena tidak ada pemeriksaan identity aplikasi.

- Simpan DB, SMTP, Kafka, dan OAuth2 credential pada secret manager/environment.
- Batasi CORS dan daftar public path dari `sdk-util`.
- Jangan gunakan default credential dari `.env.example`.
- Batasi akses filesystem ke attachment directory.
- Batasi origin WebSocket dan wajibkan permission `alert:read-notifications` pada STOMP `CONNECT`.
- Jangan memperluas Kafka trusted packages tanpa kebutuhan yang jelas.

## Catatan operasional

- Jalankan lebih dari satu instance hanya dengan PostgreSQL yang sama agar claim tetap
  terkoordinasi.
- Atur `processing-timeout` lebih panjang daripada waktu pengiriman email terlama yang valid.
- Pastikan SMTP operation tidak melebihi configured connection/read/write timeout.
- Pantau status `RETRY`, `FAILED`, `DEAD`, Kafka DLT, serta processing timeout.
- Idempotency mencegah duplikasi record creation; provider SMTP tetap menentukan jaminan delivery
  akhir ketika terjadi crash di sekitar acknowledgement.

## Struktur project

```text
src/main/java/com/mac/alert/
├── config/                 # Bean, Kafka, Jackson, template, virtual-thread configuration
├── controller/             # REST API
├── entities/               # Constant, DTO, mapper, dan domain model
├── job/                    # Scheduled alert pickup
├── repository/             # JDBC persistence dan state transition
├── service/                # Creation, dispatch, SMTP, template, dan attachment logic
├── subscriber/             # Kafka listener
└── utils/                  # Retry, failure classification, worker ID, exception handlers

src/main/resources/
├── db/migration/           # Flyway migrations
├── json/                   # Indeks serta contoh REST API dan Kafka event
├── application.yaml
└── application-local.yaml
```

Panduan kontribusi dan aturan implementasi tersedia pada `AGENTS.md`.

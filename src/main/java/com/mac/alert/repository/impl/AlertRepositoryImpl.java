package com.mac.alert.repository.impl;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mac.alert.entities.constant.AlertBodyType;
import com.mac.alert.entities.constant.AlertErrorCode;
import com.mac.alert.entities.constant.AttachmentDisposition;
import com.mac.alert.entities.constant.RecipientGroup;
import com.mac.alert.entities.constant.StorageType;
import com.mac.alert.entities.constant.TriggerSource;
import com.mac.alert.entities.model.AlertAttachment;
import com.mac.alert.entities.model.AlertHeader;
import com.mac.alert.entities.model.AlertMessage;
import com.mac.alert.entities.model.ClaimedAlert;
import com.mac.alert.entities.model.CreateAlert;
import com.mac.alert.entities.model.DeliveryFailure;
import com.mac.alert.entities.model.ExistingAlert;
import com.mac.alert.repository.AlertRepository;
import com.mac.alert.utils.DateUtil;
import com.mac.alert.utils.QueryUtil;
import com.mac.alert.utils.exception.AlertDeliveryException;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AlertRepositoryImpl implements AlertRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AlertRepositoryImpl(
            NamedParameterJdbcTemplate namedJdbcTemplate,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public List<ClaimedAlert> claimPendingAlerts(
            int batchSize,
            Duration processingTimeout,
            String workerId) {
        var parameters = new MapSqlParameterSource()
                .addValue("batchSize", batchSize)
                .addValue("workerId", workerId)
                .addValue(
                        "timeoutSeconds",
                        processingTimeout.toSeconds());

        return namedJdbcTemplate.query(
                QueryUtil.CLAIM_PENDING_SQL,
                parameters,
                (resultSet, rowNumber) -> new ClaimedAlert(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getInt("attempt_no"),
                        resultSet.getInt("max_retry")));
    }

    @Override
    @Transactional
    public Optional<ClaimedAlert> claimById(
            UUID alertId,
            Duration processingTimeout,
            String workerId) {
        var parameters = new MapSqlParameterSource()
                .addValue("alertId", alertId)
                .addValue("workerId", workerId)
                .addValue(
                        "timeoutSeconds",
                        processingTimeout.toSeconds());

        List<ClaimedAlert> result = namedJdbcTemplate.query(
                QueryUtil.CLAIM_BY_ID_SQL,
                parameters,
                (resultSet, rowNumber) -> new ClaimedAlert(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getInt("attempt_no"),
                        resultSet.getInt("max_retry")));

        return result.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public AlertMessage findMessageById(UUID alertId) {
        AlertHeader header;

        try {
            header = jdbcTemplate.queryForObject(
                    QueryUtil.FIND_ALERT_SQL,
                    (resultSet, rowNumber) -> new AlertHeader(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("sender_email"),
                            resultSet.getString("sender_name"),
                            resultSet.getString("reply_to_email"),
                            resultSet.getString("subject"),
                            resultSet.getString("body"),
                            AlertBodyType.valueOf(
                                    resultSet.getString("body_type")),
                            parseTemplateVariables(
                                    resultSet.getString(
                                            "template_variables"))),
                    alertId);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException(
                    "Alert not found or not PROCESSING: " + alertId,
                    exception);
        }

        if (header == null) {
            throw new IllegalStateException(
                    "Alert not found: " + alertId);
        }

        var recipientMap = new EnumMap<RecipientGroup, List<String>>(
                RecipientGroup.class);

        for (RecipientGroup group : RecipientGroup.values()) {
            recipientMap.put(group, new ArrayList<>());
        }

        jdbcTemplate.query(
                QueryUtil.FIND_RECIPIENTS_SQL,
                resultSet -> {
                    RecipientGroup group = RecipientGroup.valueOf(
                            resultSet.getString("recipient_type"));

                    recipientMap.get(group).add(
                            resultSet.getString("email"));
                },
                alertId);

        List<AlertAttachment> attachments = jdbcTemplate.query(
                QueryUtil.FIND_ATTACHMENTS_SQL,
                (resultSet, rowNumber) -> new AlertAttachment(
                        resultSet.getObject(
                                "id",
                                UUID.class),
                        resultSet.getString("file_name"),
                        resultSet.getString("content_type"),
                        resultSet.getLong("file_size_bytes"),
                        StorageType.valueOf(
                                resultSet.getString("storage_type")),
                        resultSet.getString("storage_key"),
                        resultSet.getString("checksum_sha256"),
                        AttachmentDisposition.valueOf(
                                resultSet.getString("disposition")),
                        resultSet.getString("content_id")),
                alertId);

        return new AlertMessage(
                header.id(),
                header.senderEmail(),
                header.senderName(),
                header.replyToEmail(),
                header.subject(),
                header.body(),
                header.bodyType(),
                header.templateVariables(),
                recipientMap.get(RecipientGroup.TO),
                recipientMap.get(RecipientGroup.CC),
                recipientMap.get(RecipientGroup.BCC),
                attachments);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(
            UUID alertId,
            int attemptNo,
            TriggerSource triggerSource,
            String providerMessageId,
            String workerId,
            Instant startedAt,
            Instant completedAt) {
        var parameters = baseParameters(
                alertId,
                attemptNo,
                triggerSource,
                workerId,
                startedAt,
                completedAt).addValue("providerMessageId", providerMessageId);

        int updated = namedJdbcTemplate.update(
                QueryUtil.UPDATE_SUCCESS_SQL,
                parameters);

        verifySingleUpdate(alertId, updated);

        namedJdbcTemplate.update(
                QueryUtil.INSERT_SUCCESS_HISTORY_SQL,
                parameters);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(
            UUID alertId,
            int attemptNo,
            TriggerSource triggerSource,
            DeliveryFailure failure,
            String targetStatus,
            Instant nextRetryAt,
            String workerId,
            Instant startedAt,
            Instant completedAt) {
        var parameters = baseParameters(
                alertId,
                attemptNo,
                triggerSource,
                workerId,
                startedAt,
                completedAt)
                .addValue("targetStatus", targetStatus)
                .addValue("failureCategory", failure.category().name())
                .addValue("retryable", failure.retryable())
                .addValue("errorCode", failure.errorCode().name())
                .addValue("errorMessage", failure.errorMessage())
                .addValue("exceptionClass", failure.exceptionClass())
                .addValue("nextRetryAt", DateUtil.toTimestamp(nextRetryAt))
                .addValue("providerResponseCode", failure.providerResponseCode());

        int updated = namedJdbcTemplate.update(
                QueryUtil.UPDATE_FAILURE_SQL,
                parameters);

        verifySingleUpdate(alertId, updated);

        namedJdbcTemplate.update(
                QueryUtil.INSERT_FAILURE_HISTORY_SQL,
                parameters);
    }

    @Override
    public boolean insertAlertRequest(
            UUID alertId,
            CreateAlert model,
            Instant createdAt) {
        var parameters = new MapSqlParameterSource()
                .addValue("id", alertId)
                .addValue(
                        "sourceSystem",
                        model.sourceSystem())
                .addValue(
                        "idempotencyKey",
                        model.idempotencyKey())
                .addValue(
                        "correlationId",
                        model.correlationId())
                .addValue(
                        "createdSource",
                        model.createdSource().name())
                .addValue(
                        "senderEmail",
                        model.senderEmail())
                .addValue(
                        "senderName",
                        model.senderName())
                .addValue(
                        "replyToEmail",
                        model.replyToEmail())
                .addValue("subject", model.subject())
                .addValue("body", model.body())
                .addValue(
                        "bodyType",
                        model.bodyType().name())
                .addValue(
                        "templateVariables",
                        serializeJson(
                                model.templateVariables()))
                .addValue("priority", model.priority())
                .addValue(
                        "scheduledAt",
                        Timestamp.from(model.scheduledAt()))
                .addValue("maxRetry", model.maxRetry())
                .addValue(
                        "createdAt",
                        Timestamp.from(createdAt));

        List<UUID> insertedIds = namedJdbcTemplate.query(
                QueryUtil.INSERT_ALERT_REQUEST_SQL,
                parameters,
                (resultSet, rowNumber) -> resultSet.getObject(
                        "id",
                        UUID.class));

        return !insertedIds.isEmpty();
    }

    @Override
    public void insertRecipients(
            UUID alertId,
            List<CreateAlert.Recipient> recipients,
            Instant createdAt) {
        if (recipients.isEmpty()) {
            return;
        }

        SqlParameterSource[] batch = recipients
                .stream()
                .map(recipient -> new MapSqlParameterSource()
                        .addValue(
                                "id",
                                UUID.randomUUID())
                        .addValue("alertId", alertId)
                        .addValue(
                                "recipientType",
                                recipient.type().name())
                        .addValue(
                                "email",
                                recipient.email())
                        .addValue(
                                "displayName",
                                recipient.displayName())
                        .addValue(
                                "createdAt",
                                Timestamp.from(createdAt)))
                .toArray(SqlParameterSource[]::new);

        namedJdbcTemplate.batchUpdate(
                QueryUtil.INSERT_RECIPIENT_SQL,
                batch);
    }

    @Override
    public void insertAttachments(
            UUID alertId,
            List<CreateAlert.Attachment> attachments,
            Instant createdAt) {
        if (attachments.isEmpty()) {
            return;
        }

        SqlParameterSource[] batch = attachments
                .stream()
                .map(attachment -> new MapSqlParameterSource()
                        .addValue(
                                "id",
                                UUID.randomUUID())
                        .addValue("alertId", alertId)
                        .addValue(
                                "fileName",
                                attachment.fileName())
                        .addValue(
                                "contentType",
                                attachment.contentType())
                        .addValue(
                                "fileSizeBytes",
                                attachment.fileSizeBytes())
                        .addValue(
                                "storageType",
                                attachment.storageType().name())
                        .addValue(
                                "storageKey",
                                attachment.storageKey())
                        .addValue(
                                "checksumSha256",
                                attachment.checksumSha256())
                        .addValue(
                                "disposition",
                                attachment.disposition().name())
                        .addValue(
                                "contentId",
                                attachment.contentId())
                        .addValue(
                                "createdAt",
                                Timestamp.from(createdAt)))
                .toArray(SqlParameterSource[]::new);

        namedJdbcTemplate.batchUpdate(
                QueryUtil.INSERT_ATTACHMENT_SQL,
                batch);
    }

    @Override
    public ExistingAlert findExistingAlert(
            String sourceSystem,
            String idempotencyKey) {
        var parameters = new MapSqlParameterSource()
                .addValue("sourceSystem", sourceSystem)
                .addValue("idempotencyKey", idempotencyKey);

        return namedJdbcTemplate.query(
                QueryUtil.FIND_EXISTING_ALERT_SQL,
                parameters,
                (resultSet, rowNumber) -> new ExistingAlert(
                        resultSet.getObject(
                                "id",
                                UUID.class),
                        resultSet.getString(
                                "status"),
                        resultSet.getTimestamp(
                                "created_at").toInstant()))
                .stream()
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Idempotent alert not found"));
    }

    private String serializeJson(
            Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(
                    value == null ? Map.of() : value);

        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "templateVariables cannot convert to JSON",
                    exception);
        }
    }

    private MapSqlParameterSource baseParameters(
            UUID alertId,
            int attemptNo,
            TriggerSource triggerSource,
            String workerId,
            Instant startedAt,
            Instant completedAt) {
        long durationMs = Math.max(
                0,
                Duration.between(
                        startedAt,
                        completedAt).toMillis());

        return new MapSqlParameterSource()
                .addValue("alertId", alertId)
                .addValue("attemptNo", attemptNo)
                .addValue(
                        "triggerSource",
                        triggerSource.name())
                .addValue("workerId", workerId)
                .addValue(
                        "startedAt",
                        Timestamp.from(startedAt))
                .addValue(
                        "completedAt",
                        Timestamp.from(completedAt))
                .addValue("durationMs", durationMs);
    }

    private void verifySingleUpdate(
            UUID alertId,
            int updatedRows) {
        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Alert is no longer owned by this worker: "
                            + alertId);
        }
    }

    private Map<String, Object> parseTemplateVariables(
            String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<Map<String, Object>>() {
                    });

        } catch (JsonProcessingException exception) {
            throw new AlertDeliveryException(
                    AlertErrorCode.TEMPLATE_RENDER_FAILED,
                    "Format template_variables not valid",
                    exception);
        }
    }

}

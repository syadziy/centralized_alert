package com.mac.alert.repository.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.alert.entities.constant.*;
import com.mac.alert.entities.model.*;
import com.mac.alert.utils.QueryUtil;
import com.mac.alert.utils.exception.AlertDeliveryException;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;

class AlertRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void coversClaimCreateBatchAndExistingOperations() throws Exception {
        NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AlertRepositoryImpl repository = new AlertRepositoryImpl(named, jdbc, new ObjectMapper());
        UUID id = UUID.randomUUID();

        when(named.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper<Object> mapper = invocation.getArgument(2);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(id);
                    if (sql.equals(QueryUtil.FIND_EXISTING_ALERT_SQL)) {
                        when(rs.getString("status")).thenReturn("PENDING");
                        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(NOW));
                    } else {
                        when(rs.getInt("attempt_no")).thenReturn(1);
                        when(rs.getInt("max_retry")).thenReturn(3);
                    }
                    return List.of(mapper.mapRow(rs, 0));
                });
        assertEquals(id, repository.claimPendingAlerts(5, Duration.ofMinutes(1), "worker").getFirst().alertId());
        assertEquals(id, repository.claimById(id, Duration.ofMinutes(1), "worker").orElseThrow().alertId());

        CreateAlert create = command();
        assertTrue(repository.insertAlertRequest(id, create, NOW));
        repository.insertRecipients(id, create.recipients(), NOW);
        repository.insertAttachments(id, create.attachments(), NOW);
        repository.insertRecipients(id, List.of(), NOW);
        repository.insertAttachments(id, List.of(), NOW);
        verify(named, times(2)).batchUpdate(anyString(), any(SqlParameterSource[].class));
        ExistingAlert existing = repository.findExistingAlert("SOURCE", "key");
        assertEquals(id, existing.alertId());

        reset(named);
        when(named.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        assertFalse(repository.claimById(id, Duration.ofMinutes(1), "worker").isPresent());
        assertFalse(repository.insertAlertRequest(id, create, NOW));
        assertThrows(IllegalStateException.class, () -> repository.findExistingAlert("SOURCE", "key"));
    }

    @Test
    void findsCompleteMessageAndHandlesMissingOrInvalidTemplateData() throws Exception {
        NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AlertRepositoryImpl repository = new AlertRepositoryImpl(named, jdbc, new ObjectMapper());
        UUID id = UUID.randomUUID();

        when(jdbc.queryForObject(eq(QueryUtil.FIND_ALERT_SQL), any(RowMapper.class), eq(id)))
                .thenAnswer(invocation -> invocation.<RowMapper<AlertHeader>>getArgument(1)
                        .mapRow(headerResultSet(id, "{\"name\":\"Ada\"}"), 0));
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            for (String type : List.of("TO", "CC", "BCC")) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("recipient_type")).thenReturn(type);
                when(rs.getString("email")).thenReturn(type.toLowerCase() + "@example.com");
                handler.processRow(rs);
            }
            return null;
        }).when(jdbc).query(eq(QueryUtil.FIND_RECIPIENTS_SQL), any(RowCallbackHandler.class), eq(id));
        when(jdbc.query(eq(QueryUtil.FIND_ATTACHMENTS_SQL), any(RowMapper.class), eq(id)))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<AlertAttachment>>getArgument(1)
                        .mapRow(attachmentResultSet(), 0)));
        AlertMessage message = repository.findMessageById(id);
        assertEquals("Ada", message.templateVariables().get("name"));
        assertEquals("to@example.com", message.to().getFirst());
        assertEquals(1, message.attachments().size());

        when(jdbc.queryForObject(eq(QueryUtil.FIND_ALERT_SQL), any(RowMapper.class), eq(id))).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> repository.findMessageById(id));
        when(jdbc.queryForObject(eq(QueryUtil.FIND_ALERT_SQL), any(RowMapper.class), eq(id)))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThrows(IllegalStateException.class, () -> repository.findMessageById(id));

        reset(jdbc);
        when(jdbc.queryForObject(eq(QueryUtil.FIND_ALERT_SQL), any(RowMapper.class), eq(id)))
                .thenAnswer(invocation -> invocation.<RowMapper<AlertHeader>>getArgument(1)
                        .mapRow(headerResultSet(id, "bad-json"), 0));
        assertThrows(AlertDeliveryException.class, () -> repository.findMessageById(id));
    }

    @Test
    void marksSuccessAndFailureOnlyForOwnedRowsAndWrapsSerialization() throws Exception {
        NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AlertRepositoryImpl repository = new AlertRepositoryImpl(named, jdbc, new ObjectMapper());
        UUID id = UUID.randomUUID();
        when(named.update(eq(QueryUtil.UPDATE_SUCCESS_SQL), any(MapSqlParameterSource.class))).thenReturn(1);
        when(named.update(eq(QueryUtil.UPDATE_FAILURE_SQL), any(MapSqlParameterSource.class))).thenReturn(1);
        repository.markSuccess(id, 1, TriggerSource.API, "message", "worker", NOW, NOW.minusSeconds(1));
        repository.markFailure(id, 1, TriggerSource.KAFKA,
                new DeliveryFailure(AlertErrorCode.UNKNOWN_ERROR, "error", "type", "451"),
                "RETRY", NOW.plusSeconds(1), "worker", NOW, NOW.plusMillis(20));
        verify(named).update(eq(QueryUtil.INSERT_SUCCESS_HISTORY_SQL), any(MapSqlParameterSource.class));
        verify(named).update(eq(QueryUtil.INSERT_FAILURE_HISTORY_SQL), any(MapSqlParameterSource.class));

        when(named.update(eq(QueryUtil.UPDATE_SUCCESS_SQL), any(MapSqlParameterSource.class))).thenReturn(0);
        assertThrows(IllegalStateException.class,
                () -> repository.markSuccess(id, 1, TriggerSource.API, "message", "worker", NOW, NOW));
        when(named.update(eq(QueryUtil.UPDATE_FAILURE_SQL), any(MapSqlParameterSource.class))).thenReturn(2);
        assertThrows(IllegalStateException.class, () -> repository.markFailure(id, 1, TriggerSource.API,
                new DeliveryFailure(AlertErrorCode.INVALID_SENDER, "bad", "type", null),
                "FAILED", null, "worker", NOW, NOW));

        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") {});
        AlertRepositoryImpl brokenRepository = new AlertRepositoryImpl(named, jdbc, broken);
        assertThrows(IllegalStateException.class,
                () -> brokenRepository.insertAlertRequest(id, command(), NOW));
    }

    private static CreateAlert command() {
        return new CreateAlert("SOURCE", "key", null, AlertCreatedSource.API, "sender@example.com", null,
                null, "subject", "body", AlertBodyType.HTML, Map.of("name", "Ada"), 3, NOW, 2,
                List.of(new CreateAlert.Recipient(RecipientType.TO, "to@example.com", null)),
                List.of(new CreateAlert.Attachment("file.txt", "text/plain", 2, StorageType.LOCAL,
                        "file.txt", null, AttachmentDisposition.ATTACHMENT, null)));
    }

    private static ResultSet headerResultSet(UUID id, String variables) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(id);
        when(rs.getString("sender_email")).thenReturn("sender@example.com");
        when(rs.getString("sender_name")).thenReturn("Sender");
        when(rs.getString("reply_to_email")).thenReturn("reply@example.com");
        when(rs.getString("subject")).thenReturn("Subject");
        when(rs.getString("body")).thenReturn("Body");
        when(rs.getString("body_type")).thenReturn("HTML");
        when(rs.getString("template_variables")).thenReturn(variables);
        return rs;
    }

    private static ResultSet attachmentResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
        when(rs.getString("file_name")).thenReturn("file.txt");
        when(rs.getString("content_type")).thenReturn("text/plain");
        when(rs.getLong("file_size_bytes")).thenReturn(2L);
        when(rs.getString("storage_type")).thenReturn("LOCAL");
        when(rs.getString("storage_key")).thenReturn("file.txt");
        when(rs.getString("disposition")).thenReturn("ATTACHMENT");
        return rs;
    }
}

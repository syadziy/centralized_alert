package com.mac.alert.repository.impl;

import com.mac.alert.entities.dto.DeliveryHistoryResponse;
import com.mac.alert.repository.DeliveryHistoryRepository;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryHistoryRepositoryImpl implements DeliveryHistoryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DeliveryHistoryRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DeliveryHistoryResponse> findAll(String result, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT h.id, h.alert_id, a.source_system, a.subject,
                       COALESCE(string_agg(r.recipient_type || ': ' || r.email, ', '
                           ORDER BY r.recipient_type, r.email), '') AS recipients,
                       h.attempt_no, h.trigger_source, h.result, h.failure_category,
                       h.retryable, h.error_code, h.error_message, h.provider_message_id,
                       h.started_at, h.completed_at, h.duration_ms, h.next_retry_at
                FROM alert_delivery_history h
                JOIN alert_request a ON a.id = h.alert_id
                LEFT JOIN alert_recipient r ON r.alert_id = h.alert_id
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        if (result != null) {
            sql.append(" WHERE h.result = :result");
            parameters.addValue("result", result);
        }
        sql.append("""
                GROUP BY h.id, a.id
                ORDER BY h.completed_at DESC, h.id
                LIMIT :limit OFFSET :offset
                """);
        return jdbcTemplate.query(sql.toString(), parameters,
                (rs, rowNum) -> new DeliveryHistoryResponse(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getObject("alert_id", java.util.UUID.class),
                        rs.getString("source_system"), rs.getString("subject"),
                        rs.getString("recipients"), rs.getInt("attempt_no"),
                        rs.getString("trigger_source"), rs.getString("result"),
                        rs.getString("failure_category"), rs.getObject("retryable", Boolean.class),
                        rs.getString("error_code"), rs.getString("error_message"),
                        rs.getString("provider_message_id"),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("completed_at").toInstant(), rs.getLong("duration_ms"),
                        rs.getTimestamp("next_retry_at") == null ? null : rs.getTimestamp("next_retry_at").toInstant()));
    }

    @Override
    public long count(String result) {
        String sql = "SELECT COUNT(*) FROM alert_delivery_history";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (result != null) {
            sql += " WHERE result = :result";
            parameters.addValue("result", result);
        }
        Long total = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return total == null ? 0 : total;
    }
}

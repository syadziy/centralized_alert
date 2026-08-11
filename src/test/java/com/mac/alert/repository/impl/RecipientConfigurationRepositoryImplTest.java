package com.mac.alert.repository.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mac.alert.entities.constant.RecipientType;
import com.mac.alert.entities.model.RecipientConfiguration;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class RecipientConfigurationRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void coversCrudQueriesAndRowMapping() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        RecipientConfigurationRepositoryImpl repository =
                new RecipientConfigurationRepositoryImpl(jdbc);
        UUID id = UUID.randomUUID();
        RecipientConfiguration configuration = new RecipientConfiguration(
                id, "PAYMENT", RecipientType.TO, "ops@example.com", "Ops", true, NOW, NOW);

        when(jdbc.update(contains("INSERT INTO"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.update(startsWith("UPDATE"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.update(startsWith("DELETE"), any(Map.class))).thenReturn(1);
        assertSame(configuration, repository.insert(configuration));
        assertTrue(repository.update(configuration));
        assertTrue(repository.delete(id));

        when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(map(invocation.getArgument(2), id)));
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(map(invocation.getArgument(2), id)));
        assertTrue(repository.findById(id).isPresent());
        assertEquals(1, repository.findAll("PAYMENT", 10, 0).size());
        assertEquals(1, repository.findAll(null, 10, 0).size());
        assertEquals(1, repository.findResolvedForSource("PAYMENT").size());

        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L, null);
        assertTrue(repository.exists("PAYMENT", RecipientType.TO, "ops@example.com", null));
        assertFalse(repository.exists("PAYMENT", RecipientType.TO, "ops@example.com", id));

        when(jdbc.update(startsWith("UPDATE"), any(MapSqlParameterSource.class))).thenReturn(0);
        when(jdbc.update(startsWith("DELETE"), any(Map.class))).thenReturn(0);
        assertFalse(repository.update(configuration));
        assertFalse(repository.delete(id));
    }

    private static RecipientConfiguration map(RowMapper<RecipientConfiguration> mapper, UUID id)
            throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("id", UUID.class)).thenReturn(id);
        when(resultSet.getString("source_system")).thenReturn("PAYMENT");
        when(resultSet.getString("recipient_type")).thenReturn("TO");
        when(resultSet.getString("email")).thenReturn("ops@example.com");
        when(resultSet.getString("display_name")).thenReturn("Ops");
        when(resultSet.getBoolean("enabled")).thenReturn(true);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(NOW));
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(NOW));
        return mapper.mapRow(resultSet, 0);
    }
}

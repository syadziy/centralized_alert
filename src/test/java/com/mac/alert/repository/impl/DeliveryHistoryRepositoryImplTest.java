package com.mac.alert.repository.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mac.alert.entities.dto.DeliveryHistoryResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class DeliveryHistoryRepositoryImplTest {

    @Test
    void returnsEmptyPagesForFilteredAndUnfilteredQueries() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DeliveryHistoryRepositoryImpl repository = new DeliveryHistoryRepositoryImpl(jdbc);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        assertTrue(repository.findAll("SUCCESS", 10, 0).isEmpty());
        assertTrue(repository.findAll(null, 10, 0).isEmpty());
        assertEquals(0, repository.count("SUCCESS"));
        assertEquals(0, repository.count(null));

        ArgumentCaptor<String> listSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).query(listSql.capture(), any(MapSqlParameterSource.class),
                any(RowMapper.class));
        assertTrue(listSql.getAllValues().get(0).contains("WHERE h.result = :result"));
        assertFalse(listSql.getAllValues().get(1).contains("WHERE h.result"));

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForObject(countSql.capture(),
                any(MapSqlParameterSource.class), eq(Long.class));
        assertTrue(countSql.getAllValues().get(0).contains("WHERE result = :result"));
        assertFalse(countSql.getAllValues().get(1).contains(" WHERE "));
    }
}

package com.mac.alert.repository.impl;

import com.mac.alert.entities.constant.RecipientType;
import com.mac.alert.entities.model.RecipientConfiguration;
import com.mac.alert.repository.RecipientConfigurationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecipientConfigurationRepositoryImpl implements RecipientConfigurationRepository {

    private static final String COLUMNS = """
            id, source_system, recipient_type, email, display_name, enabled, created_at, updated_at
            """;
    private static final String INSERT_SQL = """
            INSERT INTO alert_recipient_configuration
                (id, source_system, recipient_type, email, display_name, enabled, created_at, updated_at)
            VALUES
                (:id, :sourceSystem, :recipientType, :email, :displayName, :enabled, :createdAt, :updatedAt)
            """;
    private static final String UPDATE_SQL = """
            UPDATE alert_recipient_configuration
            SET source_system = :sourceSystem, recipient_type = :recipientType, email = :email,
                display_name = :displayName, enabled = :enabled, updated_at = :updatedAt
            WHERE id = :id
            """;
    private static final String DELETE_SQL = "DELETE FROM alert_recipient_configuration WHERE id = :id";
    private static final String FIND_BY_ID_SQL = "SELECT " + COLUMNS
            + " FROM alert_recipient_configuration WHERE id = :id";
    private static final String EXISTS_SQL = """
            SELECT COUNT(*) FROM alert_recipient_configuration
            WHERE source_system = :sourceSystem AND recipient_type = :recipientType AND email = :email
              AND (:excludedId IS NULL OR id <> :excludedId)
            """;
    private static final String FIND_ALL_SQL = "SELECT " + COLUMNS
            + " FROM alert_recipient_configuration";
    private static final String FIND_RESOLVED_SQL = "SELECT DISTINCT ON (recipient_type, email) "
            + COLUMNS + """
             FROM alert_recipient_configuration
             WHERE source_system IN ('*', :sourceSystem)
             ORDER BY recipient_type, email,
                      CASE WHEN source_system = :sourceSystem THEN 0 ELSE 1 END
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RecipientConfigurationRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RecipientConfiguration insert(RecipientConfiguration configuration) {
        jdbcTemplate.update(INSERT_SQL, parameters(configuration));
        return configuration;
    }

    @Override
    public boolean update(RecipientConfiguration configuration) {
        return jdbcTemplate.update(UPDATE_SQL, parameters(configuration)) == 1;
    }

    @Override
    public boolean delete(UUID id) {
        return jdbcTemplate.update(DELETE_SQL, Map.of("id", id)) == 1;
    }

    @Override
    public Optional<RecipientConfiguration> findById(UUID id) {
        return jdbcTemplate.query(FIND_BY_ID_SQL, Map.of("id", id), this::map).stream().findFirst();
    }

    @Override
    public boolean exists(String sourceSystem, RecipientType type, String email, UUID excludedId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("sourceSystem", sourceSystem)
                .addValue("recipientType", type.name())
                .addValue("email", email)
                .addValue("excludedId", excludedId);
        Long count = jdbcTemplate.queryForObject(EXISTS_SQL, parameters, Long.class);
        return count != null && count > 0;
    }

    @Override
    public List<RecipientConfiguration> findAll(String sourceSystem, int limit, int offset) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        StringBuilder sql = new StringBuilder(FIND_ALL_SQL);
        if (sourceSystem != null) {
            sql.append(" WHERE source_system = :sourceSystem");
            parameters.addValue("sourceSystem", sourceSystem);
        }
        sql.append(" ORDER BY source_system, recipient_type, email LIMIT :limit OFFSET :offset");
        return jdbcTemplate.query(sql.toString(), parameters, this::map);
    }

    @Override
    public List<RecipientConfiguration> findResolvedForSource(String sourceSystem) {
        return jdbcTemplate.query(FIND_RESOLVED_SQL, Map.of("sourceSystem", sourceSystem), this::map);
    }

    private MapSqlParameterSource parameters(RecipientConfiguration configuration) {
        return new MapSqlParameterSource()
                .addValue("id", configuration.id())
                .addValue("sourceSystem", configuration.sourceSystem())
                .addValue("recipientType", configuration.type().name())
                .addValue("email", configuration.email())
                .addValue("displayName", configuration.displayName())
                .addValue("enabled", configuration.enabled())
                .addValue("createdAt", configuration.createdAt())
                .addValue("updatedAt", configuration.updatedAt());
    }

    private RecipientConfiguration map(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new RecipientConfiguration(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("source_system"),
                RecipientType.valueOf(resultSet.getString("recipient_type")),
                resultSet.getString("email"),
                resultSet.getString("display_name"),
                resultSet.getBoolean("enabled"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}

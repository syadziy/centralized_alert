package com.mac.alert;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "alert.kafka.enabled=false",
    "alert.pickup.enabled=false",
    "spring.kafka.listener.auto-startup=false",
    "sdk.security.enabled=false",
    "sdk.security.method-security-enabled=false",
    "sdk.security.cors.enabled=false"
})
class AlertDatabaseIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DataSource dataSource;

    @Test
    void flywayCreatesRecipientConfigurationTableAndIndex() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer tableCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'alert_recipient_configuration'
                """, Integer.class);
        Integer indexCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'idx_alert_recipient_configuration_resolution'
                """, Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(indexCount).isEqualTo(1);
    }
}

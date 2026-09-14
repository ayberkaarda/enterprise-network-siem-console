package com.example.demo.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boots the full Spring context against a real, ephemeral PostgreSQL
 * container with the {@code postgres} profile active — Flyway on,
 * {@code ddl-auto=validate} — and asserts every migration from
 * {@code V1__baseline.sql} through {@code V8__latency_samples.sql} applied
 * cleanly. A context that fails to start (a bad migration, or Hibernate's
 * validation disagreeing with what Flyway produced) fails this test before
 * any assertion below even runs.
 */
class FlywayMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allMigrationsThroughV8ApplyCleanlyAgainstARealPostgres() {
        List<String> successfulVersions = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank", String.class);

        assertThat(successfulVersions)
                .as("every baseline-through-V8 migration must have a successful schema history row")
                .contains("1", "2", "3", "4", "5", "6", "7", "8");

        Long failedCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Long.class);
        assertThat(failedCount).as("no migration may be recorded as failed").isZero();
    }

    @Test
    void theLatencySampleTableFromV8IsUsable() {
        jdbcTemplate.update(
                "insert into latency_sample (device_id, latency, recorded_at) values (?, ?, now())", 1L, 42L);

        Long count =
                jdbcTemplate.queryForObject("select count(*) from latency_sample where device_id = ?", Long.class, 1L);
        assertThat(count).isEqualTo(1L);
    }
}

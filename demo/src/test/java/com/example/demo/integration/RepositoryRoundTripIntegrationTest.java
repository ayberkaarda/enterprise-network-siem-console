package com.example.demo.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.correlation.Rule;
import com.example.demo.correlation.RuleRepository;
import com.example.demo.device.Device;
import com.example.demo.device.DeviceRepository;
import com.example.demo.incident.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves and reloads rows through JPA repositories against the same real
 * PostgreSQL container the migration test uses, rather than against H2 —
 * proving the entity mappings and the Flyway-managed schema actually agree
 * with each other under the Postgres dialect the {@code postgres} profile
 * declares ({@code hibernate.dialect=PostgreSQLDialect}), which H2 never
 * checks.
 *
 * <p>{@code @Transactional} rolls every write back at the end of each test
 * method, so this leaves the shared container's schema history and other
 * test's rows untouched.
 */
@Transactional
class RepositoryRoundTripIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Test
    void deviceRoundTripsThroughARealPostgresInstance() {
        Device device = new Device();
        device.setName("integration-device");
        device.setIpAddress("10.99.0.5");
        device.setStatus("UNKNOWN");
        device.setLatency(0L);
        device.setDeviceType("SERVER");

        Long id = deviceRepository.save(device).getId();
        Device reloaded = deviceRepository.findById(id).orElseThrow();

        assertThat(reloaded.getName()).isEqualTo("integration-device");
        assertThat(reloaded.getIpAddress()).isEqualTo("10.99.0.5");
        assertThat(reloaded.getDeviceType()).isEqualTo("SERVER");
    }

    @Test
    void ruleRoundTripsThroughARealPostgresInstance() {
        Rule rule = new Rule();
        rule.setName("integration-rule");
        rule.setEnabled(true);
        rule.setConditionJson("{\"type\":\"flap\"}");
        rule.setThresholdCount(3);
        rule.setWindowSeconds(300);
        rule.setSeverity(Severity.MEDIUM);

        Long id = ruleRepository.save(rule).getId();
        Rule reloaded = ruleRepository.findById(id).orElseThrow();

        assertThat(reloaded.getName()).isEqualTo("integration-rule");
        assertThat(reloaded.getConditionJson()).isEqualTo("{\"type\":\"flap\"}");
        assertThat(reloaded.getSeverity()).isEqualTo(Severity.MEDIUM);
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }
}

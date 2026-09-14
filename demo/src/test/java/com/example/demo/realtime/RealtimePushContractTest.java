package com.example.demo.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.example.demo.device.Device;
import com.example.demo.device.DeviceRepository;
import com.example.demo.event.DeviceStatusChangedEvent;
import com.example.demo.incident.Incident;
import com.example.demo.incident.IncidentService;
import com.example.demo.incident.IncidentStatus;
import com.example.demo.incident.Severity;
import com.example.demo.metrics.SiemMetrics;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the live push contract: which destinations are used, and what the JSON on
 * them looks like.
 *
 * <p>Field names are asserted by name and as a complete set, not just spot
 * checked. The browser console is written against this shape without being able
 * to see this code, so a renamed or quietly added field is a broken client, and
 * a test that only checks the fields it happens to know about would not notice.
 */
@SpringBootTest
@ActiveProfiles("h2")
class RealtimePushContractTest {

    private static final long AWAIT_MILLIS = 5_000L;

    @MockitoSpyBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private MetricsBroadcaster metricsBroadcaster;

    @Autowired
    private MetricsSnapshotService metricsSnapshotService;

    @Autowired
    private SiemMetrics siemMetrics;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aDeviceStatusChangeIsPushedToTheDevicesTopic() {
        Device device = persistDevice("push-probe-01", "10.99.0.7", "ACTIVE", 42L, "FIREWALL");

        eventPublisher.publishEvent(new DeviceStatusChangedEvent(device, "UNKNOWN", "ACTIVE", 42L));

        DeviceRealtimeEvent pushed = pushedDeviceEvents().stream()
                .filter(event -> device.getId().equals(event.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no push landed on " + RealtimeTopics.DEVICES));

        assertThat(pushed.name()).isEqualTo("push-probe-01");
        assertThat(pushed.ipAddress()).isEqualTo("10.99.0.7");
        assertThat(pushed.status()).isEqualTo("ACTIVE");
        assertThat(pushed.latency()).isEqualTo(42L);
        assertThat(pushed.deviceType()).isEqualTo("FIREWALL");

        Map<String, Object> json = asJson(pushed);
        assertThat(json.keySet())
                .containsExactlyInAnyOrder("id", "name", "ipAddress", "status", "latency", "deviceType", "changedAt");
        assertThat(json.get("status")).isEqualTo("ACTIVE");
        assertThat(json.get("latency")).isEqualTo(42);
        assertThatCode(() -> Instant.parse(String.valueOf(json.get("changedAt"))))
                .doesNotThrowAnyException();
    }

    @Test
    void theLegacyAlertChannelStillCarriesDeviceStatusChanges() {
        Device device = persistDevice("legacy-alert-probe", "10.99.0.8", "INACTIVE", -1L, "SERVER");

        eventPublisher.publishEvent(new DeviceStatusChangedEvent(device, "ACTIVE", "INACTIVE", -1L));

        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq(RealtimeTopics.ALERTS), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void openingAnIncidentAndMovingItBothReachTheIncidentsTopic() {
        Incident incident =
                incidentService.create("realtime contract probe", "opened by a test", Severity.CRITICAL, 7L, "T1046");

        IncidentRealtimeEvent opened = awaitIncidentPush(incident.getId(), IncidentStatus.OPEN.name());
        assertThat(opened.title()).isEqualTo("realtime contract probe");
        assertThat(opened.severity()).isEqualTo("CRITICAL");
        assertThat(opened.sourceDeviceId()).isEqualTo(7L);

        Map<String, Object> json = asJson(opened);
        assertThat(json.keySet())
                .containsExactlyInAnyOrder(
                        "id", "title", "severity", "status", "sourceDeviceId", "createdAt", "updatedAt");
        assertThatCode(() -> Instant.parse(String.valueOf(json.get("createdAt"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> Instant.parse(String.valueOf(json.get("updatedAt"))))
                .doesNotThrowAnyException();

        incidentService.transition(incident.getId(), IncidentStatus.ACKNOWLEDGED);

        IncidentRealtimeEvent acknowledged = awaitIncidentPush(incident.getId(), IncidentStatus.ACKNOWLEDGED.name());
        assertThat(acknowledged.id()).isEqualTo(incident.getId());
        assertThat(Instant.parse(acknowledged.updatedAt())).isAfterOrEqualTo(Instant.parse(opened.updatedAt()));
    }

    @Test
    void theMetricsSnapshotIsPushedAndAlsoFeedsTheOpenIncidentGauge() {
        incidentService.create("gauge probe", null, Severity.LOW, null, null);

        metricsBroadcaster.broadcast();

        MetricsSnapshot pushed = pushedPayloads(RealtimeTopics.METRICS, MetricsSnapshot.class).stream()
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no push landed on " + RealtimeTopics.METRICS));

        assertThat(pushed.totalDevices()).isGreaterThanOrEqualTo(pushed.reachableDevices());
        assertThat(pushed.openIncidents()).isGreaterThanOrEqualTo(pushed.criticalOrHighIncidents());
        assertThat(pushed.avgLatencyMs()).isGreaterThanOrEqualTo(0.0d);
        // The same broadcast refreshes the Prometheus gauge, so it must have a
        // value once at least one incident is waiting.
        assertThat(siemMetrics.openIncidents()).isGreaterThanOrEqualTo(1L);

        Map<String, Object> json = asJson(pushed);
        assertThat(json.keySet())
                .containsExactlyInAnyOrder(
                        "totalDevices",
                        "reachableDevices",
                        "openIncidents",
                        "criticalOrHighIncidents",
                        "avgLatencyMs",
                        "timestamp");
        assertThatCode(() -> Instant.parse(String.valueOf(json.get("timestamp"))))
                .doesNotThrowAnyException();
    }

    @Test
    void anOpenCriticalIncidentIsCountedTowardsTheThreatLevel() {
        MetricsSnapshot before = metricsSnapshotService.currentSnapshot();

        Incident incident = incidentService.create("threat level probe", null, Severity.CRITICAL, null, null);

        // Compared as bounds rather than as exact equalities: the background scan
        // runs on its own timer against the same database and may open findings
        // of its own while this test is in the middle of a measurement.
        MetricsSnapshot afterOpening = metricsSnapshotService.currentSnapshot();
        assertThat(afterOpening.openIncidents()).isGreaterThanOrEqualTo(before.openIncidents() + 1);
        assertThat(afterOpening.criticalOrHighIncidents()).isGreaterThanOrEqualTo(before.criticalOrHighIncidents() + 1);

        incidentService.transition(incident.getId(), IncidentStatus.ACKNOWLEDGED);
        incidentService.transition(incident.getId(), IncidentStatus.IN_PROGRESS);
        incidentService.transition(incident.getId(), IncidentStatus.RESOLVED);

        MetricsSnapshot afterResolving = metricsSnapshotService.currentSnapshot();
        assertThat(afterResolving.openIncidents()).isLessThanOrEqualTo(afterOpening.openIncidents() - 1);
        assertThat(afterResolving.criticalOrHighIncidents())
                .isLessThanOrEqualTo(afterOpening.criticalOrHighIncidents() - 1);
    }

    // ------------------------------------------------------------- helpers

    private Device persistDevice(String name, String ip, String status, Long latency, String type) {
        Device device = new Device();
        device.setName(name);
        device.setIpAddress(ip);
        device.setStatus(status);
        device.setLatency(latency);
        device.setDeviceType(type);
        return deviceRepository.save(device);
    }

    private List<DeviceRealtimeEvent> pushedDeviceEvents() {
        return pushedPayloads(RealtimeTopics.DEVICES, DeviceRealtimeEvent.class);
    }

    private <T> List<T> pushedPayloads(String destination, Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeast(0)).convertAndSend(eq(destination), captor.capture());
        return captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    /**
     * The incident push happens after the transaction commits and on another
     * thread, so the assertion has to wait for it rather than assume it already
     * happened.
     */
    private IncidentRealtimeEvent awaitIncidentPush(Long incidentId, String status) {
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            Optional<IncidentRealtimeEvent> match =
                    pushedPayloads(RealtimeTopics.INCIDENTS, IncidentRealtimeEvent.class).stream()
                            .filter(event -> incidentId.equals(event.id()))
                            .filter(event -> status.equals(event.status()))
                            .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError(
                "incident " + incidentId + " in state " + status + " never reached " + RealtimeTopics.INCIDENTS);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asJson(Object payload) {
        return objectMapper.readValue(objectMapper.writeValueAsString(payload), Map.class);
    }
}

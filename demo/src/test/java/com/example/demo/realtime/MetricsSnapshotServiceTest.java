package com.example.demo.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.device.DeviceRepository;
import com.example.demo.incident.IncidentRepository;
import com.example.demo.incident.IncidentStatus;
import com.example.demo.incident.Severity;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the arithmetic behind the live snapshot, which is what the console's
 * threat level is derived from. The repositories are stubbed so that each rule
 * can be asserted exactly rather than against whatever rows happen to exist.
 */
class MetricsSnapshotServiceTest {

    private static final Set<IncidentStatus> UNFINISHED =
            Set.of(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS);
    private static final Set<Severity> ESCALATED = Set.of(Severity.CRITICAL, Severity.HIGH);

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final IncidentRepository incidentRepository = mock(IncidentRepository.class);
    private final MetricsSnapshotService service = new MetricsSnapshotService(deviceRepository, incidentRepository);

    @Test
    void countsComeFromTheStatusesAndSeveritiesTheConsoleCaresAbout() {
        when(deviceRepository.count()).thenReturn(12L);
        when(deviceRepository.countByStatus("ACTIVE")).thenReturn(10L);
        when(deviceRepository.averageLatencyByStatus("ACTIVE")).thenReturn(38.44d);
        when(incidentRepository.countByStatusIn(UNFINISHED)).thenReturn(3L);
        when(incidentRepository.countByStatusInAndSeverityIn(UNFINISHED, ESCALATED))
                .thenReturn(1L);

        MetricsSnapshot snapshot = service.currentSnapshot();

        assertThat(snapshot.totalDevices()).isEqualTo(12L);
        assertThat(snapshot.reachableDevices()).isEqualTo(10L);
        assertThat(snapshot.openIncidents()).isEqualTo(3L);
        assertThat(snapshot.criticalOrHighIncidents()).isEqualTo(1L);
        assertThat(snapshot.avgLatencyMs()).isEqualTo(38.4d);
        assertThatCode(() -> Instant.parse(snapshot.timestamp())).doesNotThrowAnyException();
    }

    @Test
    void resolvedAndClosedIncidentsAreNotOpenWork() {
        assertThat(MetricsSnapshotService.OPEN_STATUSES)
                .containsExactlyInAnyOrder(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS)
                .doesNotContain(IncidentStatus.RESOLVED, IncidentStatus.CLOSED);
    }

    @Test
    void onlyCriticalAndHighDriveTheThreatLevel() {
        assertThat(MetricsSnapshotService.ESCALATED_SEVERITIES)
                .containsExactlyInAnyOrder(Severity.CRITICAL, Severity.HIGH)
                .doesNotContain(Severity.MEDIUM, Severity.LOW, Severity.INFO);
    }

    @Test
    void anEstateWithNothingReachableReportsZeroLatencyRatherThanNoValue() {
        when(deviceRepository.count()).thenReturn(4L);
        when(deviceRepository.countByStatus("ACTIVE")).thenReturn(0L);
        when(deviceRepository.averageLatencyByStatus("ACTIVE")).thenReturn(null);

        MetricsSnapshot snapshot = service.currentSnapshot();

        assertThat(snapshot.reachableDevices()).isZero();
        assertThat(snapshot.avgLatencyMs()).isZero();
    }

    @Test
    void theAverageIsReportedToOneDecimalPlace() {
        when(deviceRepository.averageLatencyByStatus("ACTIVE")).thenReturn(12.3456789d);

        assertThat(service.currentSnapshot().avgLatencyMs()).isEqualTo(12.3d);
    }
}

package com.example.demo.realtime;

import com.example.demo.device.DeviceRepository;
import com.example.demo.incident.IncidentRepository;
import com.example.demo.incident.IncidentStatus;
import com.example.demo.incident.Severity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the console-wide snapshot.
 *
 * <p>Every figure is an aggregate query rather than a fetch-then-count in Java:
 * the snapshot is produced on a short timer, and loading every device and every
 * incident to count them would make the cost of watching the system grow with
 * the size of the system.
 */
@Service
public class MetricsSnapshotService {

    /** The status a device is in when its last reachability probe succeeded. */
    public static final String REACHABLE_STATUS = "ACTIVE";

    /**
     * The states that still need an analyst. RESOLVED and CLOSED are finished
     * work and are deliberately excluded, so the number is a queue depth rather
     * than a running total.
     */
    public static final Set<IncidentStatus> OPEN_STATUSES =
            Set.of(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS);

    /** Severities that drive the console's threat level indicator. */
    public static final Set<Severity> ESCALATED_SEVERITIES = Set.of(Severity.CRITICAL, Severity.HIGH);

    private final DeviceRepository deviceRepository;
    private final IncidentRepository incidentRepository;

    public MetricsSnapshotService(DeviceRepository deviceRepository, IncidentRepository incidentRepository) {
        this.deviceRepository = deviceRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    public MetricsSnapshot currentSnapshot() {
        long totalDevices = deviceRepository.count();
        long reachableDevices = deviceRepository.countByStatus(REACHABLE_STATUS);
        long openIncidents = incidentRepository.countByStatusIn(OPEN_STATUSES);
        long escalatedIncidents = incidentRepository.countByStatusInAndSeverityIn(OPEN_STATUSES, ESCALATED_SEVERITIES);

        Double averageLatency = deviceRepository.averageLatencyByStatus(REACHABLE_STATUS);

        return new MetricsSnapshot(
                totalDevices,
                reachableDevices,
                openIncidents,
                escalatedIncidents,
                roundToTenth(averageLatency),
                Instant.now().truncatedTo(ChronoUnit.MILLIS).toString());
    }

    /**
     * Trims the average to one decimal. A latency average carried to fifteen
     * digits suggests a precision the underlying millisecond measurements do not
     * have, and it changes on every snapshot for no observable reason.
     */
    private static double roundToTenth(Double value) {
        if (value == null || value.isNaN()) {
            return 0.0d;
        }
        return Math.round(value * 10.0d) / 10.0d;
    }
}

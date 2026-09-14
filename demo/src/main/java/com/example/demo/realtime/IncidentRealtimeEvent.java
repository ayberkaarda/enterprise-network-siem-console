package com.example.demo.realtime;

import com.example.demo.incident.Incident;

import java.time.Instant;

/**
 * Payload pushed to {@link RealtimeTopics#INCIDENTS}, both when an incident is
 * opened and on every accepted state transition.
 *
 * <p>Built while the originating transaction is still open, so it describes the
 * row exactly as it was written; the push itself happens later, after that
 * transaction commits.
 *
 * @param severity   severity name, for example {@code CRITICAL}
 * @param status     lifecycle state name, for example {@code OPEN}
 * @param createdAt  ISO-8601 instant, in UTC
 * @param updatedAt  ISO-8601 instant, in UTC
 */
public record IncidentRealtimeEvent(
        Long id,
        String title,
        String severity,
        String status,
        Long sourceDeviceId,
        String createdAt,
        String updatedAt) {

    public static IncidentRealtimeEvent from(Incident incident) {
        return new IncidentRealtimeEvent(
                incident.getId(),
                incident.getTitle(),
                incident.getSeverity() == null ? null : incident.getSeverity().name(),
                incident.getStatus() == null ? null : incident.getStatus().name(),
                incident.getSourceDeviceId(),
                format(incident.getCreatedAt()),
                format(incident.getUpdatedAt()));
    }

    private static String format(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}

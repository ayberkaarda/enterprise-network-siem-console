package com.example.demo.incident;

import java.time.Instant;

/**
 * Read model for an incident. Comments are intentionally absent: they are
 * unbounded in number and fetched on demand from
 * {@code GET /api/v1/incidents/{id}/comments}, so a list page never drags an
 * arbitrary amount of note text along with it.
 */
public record IncidentResponse(
        Long id,
        String title,
        String description,
        Severity severity,
        IncidentStatus status,
        Long sourceDeviceId,
        String mitreTechniqueId,
        String assignee,
        Instant createdAt,
        Instant updatedAt) {}

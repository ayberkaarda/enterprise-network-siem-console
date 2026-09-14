package com.example.demo.incident;

/**
 * Lifecycle states an incident moves through. The legal edges between them are
 * owned by {@link IncidentStateMachine}, not by this enum.
 */
public enum IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}

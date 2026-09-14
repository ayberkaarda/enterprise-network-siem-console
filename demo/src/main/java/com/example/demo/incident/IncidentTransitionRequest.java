package com.example.demo.incident;

import jakarta.validation.constraints.NotNull;

/**
 * Write model for advancing an incident along its lifecycle.
 */
public record IncidentTransitionRequest(@NotNull IncidentStatus newStatus) {}

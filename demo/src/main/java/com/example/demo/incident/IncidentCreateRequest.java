package com.example.demo.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Write model for opening an incident by hand. The lifecycle status is not
 * accepted from the caller: every incident starts at {@code OPEN} and moves on
 * only through the transition endpoint.
 */
public record IncidentCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @NotNull Severity severity,
        Long sourceDeviceId,
        @Size(max = 32) String mitreTechniqueId,
        @Size(max = 255) String assignee) {
}

package com.example.demo.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Write model for attaching an analyst note to an incident.
 */
public record IncidentCommentRequest(
        @NotBlank @Size(max = 255) String author,
        @NotBlank @Size(max = 2000) String body) {}

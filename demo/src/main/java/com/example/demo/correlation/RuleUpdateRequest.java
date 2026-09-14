package com.example.demo.correlation;

import com.example.demo.incident.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Write model for replacing a rule's editable fields via {@code PUT}.
 * Deliberately kept as its own type rather than reusing
 * {@link RuleCreateRequest}: the two sit at different points of the contract
 * (create vs. replace) that happen to share a shape today, and keeping them
 * separate lets one change later without silently changing the other.
 */
public record RuleUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        boolean enabled,
        @NotBlank @Size(max = 2000) String conditionJson,
        @Positive int thresholdCount,
        @Positive int windowSeconds,
        @NotNull Severity severity) {}

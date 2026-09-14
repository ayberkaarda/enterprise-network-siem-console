package com.example.demo.correlation;

import com.example.demo.incident.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Write model for defining a new correlation rule. {@code conditionJson} is
 * checked for syntactic JSON validity and a recognised {@code type}
 * discriminator by {@link RuleService}, not by bean validation here, because
 * both checks need to parse the string rather than match it against a regex.
 */
public record RuleCreateRequest(
        @NotBlank @Size(max = 255) String name,
        boolean enabled,
        @NotBlank @Size(max = 2000) String conditionJson,
        @Positive int thresholdCount,
        @Positive int windowSeconds,
        @NotNull Severity severity) {}

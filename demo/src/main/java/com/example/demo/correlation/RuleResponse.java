package com.example.demo.correlation;

import com.example.demo.incident.Severity;
import java.time.Instant;

/**
 * Read model for a correlation rule. Mirrors {@link Rule} field for field; see
 * that class's Javadoc for the {@code conditionJson} schema.
 */
public record RuleResponse(
        Long id,
        String name,
        boolean enabled,
        String conditionJson,
        int thresholdCount,
        int windowSeconds,
        Severity severity,
        Instant createdAt) {}

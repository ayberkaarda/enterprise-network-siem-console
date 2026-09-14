package com.example.demo.correlation;

import com.example.demo.incident.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A correlation rule, stored as data so the shipped rule set can be edited,
 * disabled or extended without recompiling. {@link CorrelationEngine} contains
 * one evaluator per <em>rule type</em>; it never contains a threshold, a time
 * window or a device name.
 *
 * <p><strong>Condition JSON schema.</strong> {@code conditionJson} always
 * carries a {@code "type"} discriminator plus whatever extra parameters that
 * type needs. The two parameters every type shares — how many occurrences and
 * over what period — live in the dedicated {@code thresholdCount} and
 * {@code windowSeconds} columns instead of inside the blob, so they can be
 * queried, indexed and edited without JSON surgery. Supported types:
 *
 * <pre>
 * {"type":"flap"}
 *     Fires when one device changes status thresholdCount or more times within
 *     windowSeconds.
 *
 * {"type":"subnet_outage","downStatuses":["INACTIVE"],"prefixOctets":3}
 *     Fires when thresholdCount or more distinct devices sharing an IPv4 prefix
 *     (first prefixOctets octets, default 3, i.e. a /24) enter one of
 *     downStatuses within windowSeconds. Possible switch or uplink failure.
 *
 * {"type":"latency_anomaly"}
 *     Fires when a device reports a latency more than three standard deviations
 *     above its own exponentially weighted baseline. The statistical work is
 *     done by the latency baseline service; this rule decides the severity the
 *     resulting incident is raised at, and windowSeconds acts as the
 *     per-device cool-down so one degraded link does not open an incident on
 *     every probe.
 *
 * {"type":"event_burst","category":"AUTH"}
 *     Fires when thresholdCount or more ingested events from the same source
 *     arrive within windowSeconds. When "category" is present only events of
 *     that category are counted. No row of this type ships by default; the
 *     evaluator exists so operators can add one as data.
 * </pre>
 *
 * The table is named {@code correlation_rule} rather than {@code rule} because
 * bare {@code RULE} collides with SQL statement syntax in several engines and
 * would force quoting at every use site.
 */
@Entity
@Table(name = "correlation_rule")
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 2000)
    private String conditionJson;

    @Column(nullable = false)
    private int thresholdCount;

    @Column(nullable = false)
    private int windowSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Severity severity;

    @Column(nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    public Rule() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public void setConditionJson(String conditionJson) {
        this.conditionJson = conditionJson;
    }

    public int getThresholdCount() {
        return thresholdCount;
    }

    public void setThresholdCount(int thresholdCount) {
        this.thresholdCount = thresholdCount;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

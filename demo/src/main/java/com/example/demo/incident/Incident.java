package com.example.demo.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

/**
 * A security or availability finding that an analyst works to closure.
 *
 * <p>{@code sourceDeviceId} deliberately stores a bare identifier rather than a
 * JPA association: an incident must survive the deletion of the device that
 * triggered it, and the incident list is read far more often than the device it
 * points at, so an eagerly joinable relationship would cost more than it pays
 * for.
 */
@Entity
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentStatus status;

    /** Identifier of the related device, when the incident originated from one. */
    private Long sourceDeviceId;

    /** Optional MITRE ATT&amp;CK technique label, for example {@code T1110}. */
    @Column(length = 32)
    private String mitreTechniqueId;

    private String assignee;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    public Incident() {}

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public Long getSourceDeviceId() {
        return sourceDeviceId;
    }

    public void setSourceDeviceId(Long sourceDeviceId) {
        this.sourceDeviceId = sourceDeviceId;
    }

    public String getMitreTechniqueId() {
        return mitreTechniqueId;
    }

    public void setMitreTechniqueId(String mitreTechniqueId) {
        this.mitreTechniqueId = mitreTechniqueId;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

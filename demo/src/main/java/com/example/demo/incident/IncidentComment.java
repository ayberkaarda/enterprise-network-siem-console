package com.example.demo.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import java.time.Instant;

/**
 * An analyst note attached to an incident. Like {@link Incident}, the link is a
 * bare identifier column rather than a JPA association, so comments can be
 * paged independently of the incident they belong to.
 */
@Entity
public class IncidentComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    public IncidentComment() {}

    public IncidentComment(Long incidentId, String author, String body) {
        this.incidentId = incidentId;
        this.author = author;
        this.body = body;
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

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

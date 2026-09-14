package com.example.demo.common;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;

    private LocalDateTime timestamp;

    /**
     * Who caused this entry: an authenticated username, or {@code "system"} for
     * the unattended scheduled scan. Nullable because rows written before this
     * column existed have no such value and none is invented for them; see
     * {@code AuditActor} for how a new row's value is chosen.
     */
    private String actor;

    /** Required by JPA. */
    public AuditLog() {
    }

    public AuditLog(String message, LocalDateTime timestamp, String actor) {
        this.message = message;
        this.timestamp = timestamp;
        this.actor = actor;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }
}

package com.example.demo.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One reachability probe's latency reading for a device, kept for history so
 * the console can chart a device's latency over time instead of only showing
 * its current value.
 *
 * <p>Placed in the {@code device} package rather than a separate
 * {@code telemetry} package: the only producer is
 * {@link DeviceService#checkDeviceStatus(Long)} and the only consumer is the
 * device latency history endpoint on {@link DeviceQueryController}, so the
 * type has no reason to live outside the package that already owns both ends
 * of it.
 *
 * <p>{@code deviceId} is a plain column rather than a JPA relationship to
 * {@link Device}, the same choice already made for {@code Incident.sourceDeviceId}:
 * a sample never needs to navigate back to a loaded device, and dropping the
 * relationship avoids a lazy-loading trap on a table expected to grow much
 * larger than the device table itself.
 */
@Entity
@Table(name = "latency_sample")
public class LatencySample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private Long latency;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** Required by JPA. */
    public LatencySample() {}

    public LatencySample(Long deviceId, Long latency) {
        this.deviceId = deviceId;
        this.latency = latency;
    }

    @PrePersist
    void onCreate() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getLatency() {
        return latency;
    }

    public void setLatency(Long latency) {
        this.latency = latency;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}

package com.example.demo.event;

import com.example.demo.entity.Device;

public class DeviceStatusChangedEvent {
    private final Device device;
    private final String oldStatus;
    private final String newStatus;
    private final Long latency;

    public DeviceStatusChangedEvent(Device device, String oldStatus, String newStatus, Long latency) {
        this.device = device;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.latency = latency;
    }

    // Getter metotları
    public Device getDevice() { return device; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public Long getLatency() { return latency; }
}
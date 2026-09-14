package com.example.demo.device;

/**
 * Read model for a monitored device. Field names deliberately mirror the entity
 * so the JSON representation stays identical to what existing clients consume.
 */
public record DeviceResponse(Long id, String name, String ipAddress, String status, Long latency, String deviceType) {}

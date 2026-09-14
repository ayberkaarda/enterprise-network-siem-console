package com.example.demo.realtime;

import com.example.demo.device.Device;
import com.example.demo.event.DeviceStatusChangedEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Payload pushed to {@link RealtimeTopics#DEVICES}.
 *
 * <p>A snapshot rather than a reference to the live entity: the device object
 * carried by the internal event is still owned by the scanning thread, so any
 * field read after the listener returns could already describe a later probe.
 *
 * <p>{@code changedAt} is a string rather than a temporal type on purpose. The
 * wire format of a timestamp is part of the contract with the browser client,
 * and leaving it to the message converter's date handling would make that format
 * depend on configuration rather than on this declaration.
 *
 * @param changedAt ISO-8601 instant, in UTC, at which this push was produced
 */
public record DeviceRealtimeEvent(
        Long id, String name, String ipAddress, String status, Long latency, String deviceType, String changedAt) {

    public static DeviceRealtimeEvent from(DeviceStatusChangedEvent event) {
        Device device = event.getDevice();
        return new DeviceRealtimeEvent(
                device.getId(),
                device.getName(),
                device.getIpAddress(),
                event.getNewStatus(),
                event.getLatency(),
                device.getDeviceType(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS).toString());
    }
}

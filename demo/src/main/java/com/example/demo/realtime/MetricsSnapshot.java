package com.example.demo.realtime;

/**
 * Payload pushed to {@link RealtimeTopics#METRICS}: the state of the estate in
 * one message, small enough to send on a short timer.
 *
 * @param totalDevices            every monitored device, whatever its state
 * @param reachableDevices        devices whose last probe succeeded
 * @param openIncidents           incidents not yet resolved or closed
 * @param criticalOrHighIncidents the open ones at CRITICAL or HIGH, which is
 *                                what the console's threat level is derived from
 * @param avgLatencyMs            mean last-known latency across reachable
 *                                devices, or 0 when none are reachable
 * @param timestamp               ISO-8601 instant, in UTC, at which the snapshot
 *                                was taken
 */
public record MetricsSnapshot(
        long totalDevices,
        long reachableDevices,
        long openIncidents,
        long criticalOrHighIncidents,
        double avgLatencyMs,
        String timestamp) {}

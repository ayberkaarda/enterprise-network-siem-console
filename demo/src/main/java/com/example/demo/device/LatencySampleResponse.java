package com.example.demo.device;

import java.time.Instant;

/**
 * One point on a device's latency history chart.
 */
public record LatencySampleResponse(Long latency, Instant recordedAt) {}

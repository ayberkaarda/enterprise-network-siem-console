package com.example.demo.anomaly;

/**
 * Immutable snapshot of one device's latency baseline.
 *
 * @param sampleCount       how many samples have been folded in so far
 * @param mean              exponentially weighted moving average, in milliseconds
 * @param variance          exponentially weighted variance, in milliseconds squared
 */
public record LatencyBaseline(long sampleCount, double mean, double variance) {

    public double standardDeviation() {
        return variance <= 0.0 ? 0.0 : Math.sqrt(variance);
    }
}

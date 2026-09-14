package com.example.demo.anomaly;

import com.example.demo.event.DeviceStatusChangedEvent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Per-device latency baseline, maintained with an exponentially weighted moving
 * average and its matching exponentially weighted variance. No statistics
 * library is involved and no history is re-read from the database: each new
 * sample folds into a constant-size snapshot, so memory stays proportional to
 * the number of monitored devices rather than to the number of probes.
 *
 * <p>The update is the standard incremental EWMA/EWMV pair:
 * <pre>
 *   diff     = x - mean
 *   mean'    = mean + alpha * diff
 *   variance'= (1 - alpha) * (variance + diff * alpha * diff)
 * </pre>
 * with {@code alpha = 0.2}, which gives roughly a ten-sample memory: recent
 * behaviour dominates, but a single spike cannot move the baseline far enough
 * to hide the next one.
 */
@Component
public class LatencyBaselineService {

    /** Smoothing factor; higher means the baseline forgets older samples faster. */
    static final double ALPHA = 0.2d;

    /** How many standard deviations above the mean counts as anomalous. */
    static final double SIGMA_THRESHOLD = 3.0d;

    /**
     * Samples required before the baseline is trusted. Below this a device has
     * not shown enough normal behaviour for "abnormal" to mean anything.
     */
    static final long MIN_SAMPLES = 5L;

    /**
     * Floor on the standard deviation used for comparison. A device that has
     * reported exactly the same latency every time has zero measured jitter,
     * which would make any threshold of the form {@code mean + k * sd} collapse
     * onto the mean and flag ordinary rounding noise. One millisecond keeps the
     * comparison well defined without making it trigger-happy.
     */
    static final double MIN_STANDARD_DEVIATION_MS = 1.0d;

    private final Map<Long, LatencyBaseline> baselines = new ConcurrentHashMap<>();

    /**
     * Folds the latency carried by a device status change into that device's
     * baseline.
     *
     * <p>Runs at the lowest listener precedence on purpose: the correlation
     * engine has to compare the incoming value against the baseline as it stood
     * <em>before</em> this sample, otherwise every spike would partly absorb
     * itself and the deviation would be understated.
     */
    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onDeviceStatusChanged(DeviceStatusChangedEvent event) {
        if (event.getDevice() == null || event.getDevice().getId() == null) {
            return;
        }
        record(event.getDevice().getId(), event.getLatency());
    }

    /**
     * Folds one latency sample into a device's baseline. Negative values are
     * the unreachable sentinel used by the probe loop, not a measurement, and
     * are ignored.
     *
     * @return the updated baseline, or the unchanged one when the sample was skipped
     */
    public LatencyBaseline record(Long deviceId, Long latencyMillis) {
        if (deviceId == null || latencyMillis == null || latencyMillis < 0) {
            return deviceId == null ? null : baselines.get(deviceId);
        }
        double sample = latencyMillis.doubleValue();
        return baselines.compute(deviceId, (key, current) -> fold(current, sample));
    }

    private LatencyBaseline fold(LatencyBaseline current, double sample) {
        if (current == null) {
            return new LatencyBaseline(1L, sample, 0.0d);
        }
        double diff = sample - current.mean();
        double increment = ALPHA * diff;
        double mean = current.mean() + increment;
        double variance = (1.0d - ALPHA) * (current.variance() + diff * increment);
        return new LatencyBaseline(current.sampleCount() + 1, mean, variance);
    }

    /**
     * @return true when {@code latestLatency} sits more than three standard
     *         deviations above the device's current baseline mean
     */
    public boolean isAnomalous(Long deviceId, long latestLatency) {
        if (deviceId == null || latestLatency < 0) {
            return false;
        }
        LatencyBaseline baseline = baselines.get(deviceId);
        if (baseline == null || baseline.sampleCount() < MIN_SAMPLES) {
            return false;
        }
        double deviation = Math.max(baseline.standardDeviation(), MIN_STANDARD_DEVIATION_MS);
        return latestLatency > baseline.mean() + SIGMA_THRESHOLD * deviation;
    }

    public Optional<LatencyBaseline> baselineFor(Long deviceId) {
        return Optional.ofNullable(deviceId == null ? null : baselines.get(deviceId));
    }

    /** Drops all accumulated baselines; used by tests and after a device purge. */
    public void reset() {
        baselines.clear();
    }
}

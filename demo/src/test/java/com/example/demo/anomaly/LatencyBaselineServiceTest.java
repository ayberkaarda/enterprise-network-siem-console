package com.example.demo.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.demo.device.Device;
import com.example.demo.event.DeviceStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The baseline is an arithmetic claim, so it is tested arithmetically: a fixed
 * input sequence has exactly one correct mean and variance, worked out by hand
 * from the documented update rule.
 */
class LatencyBaselineServiceTest {

    private static final long DEVICE_ID = 42L;

    private LatencyBaselineService service;

    @BeforeEach
    void setUp() {
        service = new LatencyBaselineService();
    }

    @Test
    void firstSampleBecomesTheBaselineWithNoSpread() {
        service.record(DEVICE_ID, 100L);

        LatencyBaseline baseline = service.baselineFor(DEVICE_ID).orElseThrow();
        assertThat(baseline.sampleCount()).isEqualTo(1L);
        assertThat(baseline.mean()).isEqualTo(100.0d);
        assertThat(baseline.variance()).isEqualTo(0.0d);
        assertThat(baseline.standardDeviation()).isEqualTo(0.0d);
    }

    @Test
    void knownSequenceProducesTheHandCalculatedBaseline() {
        // alpha = 0.2
        // sample 100 -> mean 100, variance 0
        // sample 200 -> diff 100, increment 20, mean 120,
        //               variance 0.8 * (0 + 100 * 20) = 1600, deviation 40
        service.record(DEVICE_ID, 100L);
        service.record(DEVICE_ID, 200L);

        LatencyBaseline baseline = service.baselineFor(DEVICE_ID).orElseThrow();
        assertThat(baseline.sampleCount()).isEqualTo(2L);
        assertThat(baseline.mean()).isCloseTo(120.0d, within(1e-9d));
        assertThat(baseline.variance()).isCloseTo(1600.0d, within(1e-9d));
        assertThat(baseline.standardDeviation()).isCloseTo(40.0d, within(1e-9d));
    }

    @Test
    void stableDeviceThenSpikeIsFlaggedButOrdinaryJitterIsNot() {
        for (int i = 0; i < 10; i++) {
            service.record(DEVICE_ID, 20L);
        }

        LatencyBaseline baseline = service.baselineFor(DEVICE_ID).orElseThrow();
        assertThat(baseline.mean()).isCloseTo(20.0d, within(1e-9d));
        assertThat(baseline.standardDeviation()).isCloseTo(0.0d, within(1e-9d));

        // Deviation floor of 1 ms puts the threshold at 20 + 3 * 1 = 23 ms.
        assertThat(service.isAnomalous(DEVICE_ID, 22L)).isFalse();
        assertThat(service.isAnomalous(DEVICE_ID, 500L)).isTrue();
    }

    @Test
    void noVerdictBeforeEnoughSamples() {
        service.record(DEVICE_ID, 20L);
        service.record(DEVICE_ID, 20L);
        service.record(DEVICE_ID, 20L);

        assertThat(service.baselineFor(DEVICE_ID).orElseThrow().sampleCount()).isEqualTo(3L);
        assertThat(service.isAnomalous(DEVICE_ID, 5000L)).isFalse();
    }

    @Test
    void unknownDeviceIsNeverAnomalous() {
        assertThat(service.isAnomalous(999L, 5000L)).isFalse();
        assertThat(service.baselineFor(999L)).isEmpty();
    }

    @Test
    void unreachableSentinelIsNotASample() {
        for (int i = 0; i < 6; i++) {
            service.record(DEVICE_ID, 30L);
        }
        long countBefore = service.baselineFor(DEVICE_ID).orElseThrow().sampleCount();

        service.record(DEVICE_ID, -1L);

        LatencyBaseline baseline = service.baselineFor(DEVICE_ID).orElseThrow();
        assertThat(baseline.sampleCount()).isEqualTo(countBefore);
        assertThat(baseline.mean()).isCloseTo(30.0d, within(1e-9d));
        assertThat(service.isAnomalous(DEVICE_ID, -1L)).isFalse();
    }

    @Test
    void baselinesAreKeptPerDevice() {
        for (int i = 0; i < 6; i++) {
            service.record(1L, 10L);
            service.record(2L, 400L);
        }

        assertThat(service.baselineFor(1L).orElseThrow().mean()).isCloseTo(10.0d, within(1e-9d));
        assertThat(service.baselineFor(2L).orElseThrow().mean()).isCloseTo(400.0d, within(1e-9d));
        assertThat(service.isAnomalous(1L, 100L)).isTrue();
        assertThat(service.isAnomalous(2L, 100L)).isFalse();
    }

    @Test
    void statusChangeEventFeedsTheBaseline() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setName("edge-router");
        device.setIpAddress("10.0.0.1");

        for (int i = 0; i < 6; i++) {
            service.onDeviceStatusChanged(new DeviceStatusChangedEvent(device, "ACTIVE", "ACTIVE", 50L));
        }

        LatencyBaseline baseline = service.baselineFor(DEVICE_ID).orElseThrow();
        assertThat(baseline.sampleCount()).isEqualTo(6L);
        assertThat(baseline.mean()).isCloseTo(50.0d, within(1e-9d));
    }

    @Test
    void resetClearsEverything() {
        service.record(DEVICE_ID, 10L);
        service.reset();
        assertThat(service.baselineFor(DEVICE_ID)).isEmpty();
    }
}

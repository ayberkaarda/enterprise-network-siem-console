package com.example.demo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The operational meters this service exports, in one place.
 *
 * <p>They answer three different questions and are therefore three different
 * instrument types: how long a scan cycle takes (a timer, because the
 * distribution matters more than any single reading), how many incidents are
 * waiting for an analyst right now (a gauge, because it goes up and down), and
 * how much is arriving at the ingestion endpoint (a counter, because rate is
 * derived from a monotonic total rather than stored as one).
 *
 * <p>All three are visible at {@code /actuator/prometheus}, where the dots
 * become underscores: {@code siem_scan_duration_seconds},
 * {@code siem_incidents_open} and {@code siem_events_ingest_rate_total}.
 */
@Component
public class SiemMetrics {

    private final Timer scanDuration;
    private final Counter ingestedEvents;

    /**
     * Backing value for the open-incident gauge. The gauge reads this field
     * instead of counting rows itself: a gauge function runs on every scrape,
     * and a scrape interval of a few seconds would turn observability into a
     * steady stream of database queries. The value is refreshed by the live
     * metrics snapshot, which has to compute it anyway.
     */
    private final AtomicLong openIncidents = new AtomicLong();

    public SiemMetrics(MeterRegistry meterRegistry) {
        this.scanDuration = Timer.builder("siem.scan.duration")
                .description("Wall clock time of one complete device scan cycle")
                .register(meterRegistry);

        this.ingestedEvents = Counter.builder("siem.events.ingest.rate")
                .description("Events accepted by the ingestion endpoint")
                .register(meterRegistry);

        Gauge.builder("siem.incidents.open", openIncidents, AtomicLong::doubleValue)
                .description("Incidents still open, acknowledged or in progress")
                .register(meterRegistry);
    }

    /** Times one full scan cycle. */
    public void recordScan(Runnable scan) {
        scanDuration.record(scan);
    }

    /** Counts one event accepted for ingestion. */
    public void countIngestedEvent() {
        ingestedEvents.increment();
    }

    /** Publishes the latest open-incident count to the gauge. */
    public void updateOpenIncidents(long count) {
        openIncidents.set(count);
    }

    /** Visible for tests: the value the gauge currently reports. */
    public long openIncidents() {
        return openIncidents.get();
    }
}

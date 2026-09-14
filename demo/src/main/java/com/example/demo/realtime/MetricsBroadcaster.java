package com.example.demo.realtime;

import com.example.demo.metrics.SiemMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes the console-wide snapshot on a timer.
 *
 * <p>Unlike the device scan, this task is deliberately <em>not</em> guarded by a
 * distributed lock. The scan writes to shared state and must happen once for the
 * whole deployment; this one only reads, and each instance has to feed the
 * clients connected to it. Locking it would leave every instance but one pushing
 * nothing.
 *
 * <p>The same snapshot also refreshes the open-incident gauge, so the number the
 * dashboards scrape and the number the console displays come from one reading
 * rather than from two that can disagree.
 */
@Component
public class MetricsBroadcaster {

    /**
     * A SOC display should feel live without the figures twitching, and the
     * underlying scan cycle is much slower than this anyway.
     */
    static final long BROADCAST_INTERVAL_MS = 10_000L;

    private static final Logger log = LoggerFactory.getLogger(MetricsBroadcaster.class);

    private final MetricsSnapshotService metricsSnapshotService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SiemMetrics siemMetrics;

    public MetricsBroadcaster(
            MetricsSnapshotService metricsSnapshotService,
            SimpMessagingTemplate messagingTemplate,
            SiemMetrics siemMetrics) {
        this.metricsSnapshotService = metricsSnapshotService;
        this.messagingTemplate = messagingTemplate;
        this.siemMetrics = siemMetrics;
    }

    @Scheduled(fixedRate = BROADCAST_INTERVAL_MS)
    public void broadcast() {
        try {
            MetricsSnapshot snapshot = metricsSnapshotService.currentSnapshot();
            siemMetrics.updateOpenIncidents(snapshot.openIncidents());
            messagingTemplate.convertAndSend(RealtimeTopics.METRICS, snapshot);
        } catch (Exception ex) {
            // One missed snapshot is not worth killing the timer over; the next
            // tick recomputes everything from scratch.
            log.warn("Could not publish the live metrics snapshot", ex);
        }
    }
}

package com.example.demo.realtime;

/**
 * The STOMP destinations this service pushes to.
 *
 * <p>They are constants because the destination string is a contract with every
 * client that subscribes to it: a typo in a literal would not fail the build, it
 * would simply produce a channel nobody is listening on.
 */
public final class RealtimeTopics {

    /** One message per device status change. */
    public static final String DEVICES = "/topic/devices";

    /** One message when an incident is opened and one per accepted transition. */
    public static final String INCIDENTS = "/topic/incidents";

    /** Periodic console-wide snapshot. */
    public static final String METRICS = "/topic/metrics";

    /**
     * Free-text operator alerts. Predates the structured topics above and is
     * still consumed by the deployed console, so it keeps its existing shape.
     */
    public static final String ALERTS = "/topic/alerts";

    private RealtimeTopics() {
    }
}

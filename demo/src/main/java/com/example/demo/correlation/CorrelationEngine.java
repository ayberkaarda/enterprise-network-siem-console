package com.example.demo.correlation;

import com.example.demo.anomaly.LatencyBaselineService;
import com.example.demo.correlation.threatintel.ThreatIntelProvider;
import com.example.demo.device.Device;
import com.example.demo.event.DeviceStatusChangedEvent;
import com.example.demo.incident.Incident;
import com.example.demo.incident.IncidentService;
import com.example.demo.incident.Severity;
import com.example.demo.ingestion.IngestedEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Evaluates the stored correlation rules against the live event stream.
 *
 * <p>Two properties drive the whole design:
 *
 * <ul>
 *   <li><strong>Rules are data.</strong> This class holds one evaluator per rule
 *       <em>type</em>. Thresholds, time windows, severities and the rule set
 *       itself come from the {@code correlation_rule} table; adding a rule is an
 *       insert, not a code change.</li>
 *   <li><strong>Windows live in memory.</strong> Sliding window state is kept in
 *       bounded Caffeine caches. An incoming event is matched against what is
 *       already in memory; the raw event history is never re-queried from the
 *       database per event, which is what would make ingestion cost grow with
 *       the size of the log.</li>
 * </ul>
 *
 * <p>The rule set itself is also cached: it is compiled once (condition JSON
 * parsed into a tree) by {@link #reloadRules()} and refreshed on a timer, so the
 * hot path performs no query and no JSON parsing.
 */
@Component
public class CorrelationEngine {

    static final String TYPE_FLAP = "flap";
    static final String TYPE_SUBNET_OUTAGE = "subnet_outage";
    static final String TYPE_LATENCY_ANOMALY = "latency_anomaly";
    static final String TYPE_EVENT_BURST = "event_burst";

    /** Cool-down applied to threat intelligence hits, independent of any rule. */
    static final Duration THREAT_INTEL_COOLDOWN = Duration.ofMinutes(15);

    private static final int MAX_TRACKED_KEYS = 20_000;
    private static final int MAX_TIMESTAMPS_PER_KEY = 128;
    private static final Duration STATE_RETENTION = Duration.ofHours(1);
    private static final int DEFAULT_PREFIX_OCTETS = 3;
    private static final Set<String> DEFAULT_DOWN_STATUSES = Set.of("INACTIVE");

    private static final Logger log = LoggerFactory.getLogger(CorrelationEngine.class);

    private final RuleRepository ruleRepository;
    private final IncidentService incidentService;
    private final LatencyBaselineService latencyBaselineService;
    private final ThreatIntelProvider threatIntelProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** Compiled snapshot of the enabled rules; replaced wholesale on reload. */
    private volatile List<CompiledRule> activeRules = List.of();

    /** deviceId to the recent status change timestamps for that device. */
    private final Cache<Long, Deque<Long>> deviceStatusChanges;

    /** "ruleId|subnet" to the last time each device under it went down. */
    private final Cache<String, Map<Long, Long>> subnetOutages;

    /** "ruleId|source" to the recent ingestion timestamps for that source. */
    private final Cache<String, Deque<Long>> sourceEvents;

    /** Arbitrary key to the epoch millis at which it last produced an incident. */
    private final Cache<String, Long> lastFired;

    @Autowired
    public CorrelationEngine(
            RuleRepository ruleRepository,
            IncidentService incidentService,
            LatencyBaselineService latencyBaselineService,
            ThreatIntelProvider threatIntelProvider,
            ObjectMapper objectMapper) {
        this(
                ruleRepository,
                incidentService,
                latencyBaselineService,
                threatIntelProvider,
                objectMapper,
                Clock.systemUTC());
    }

    CorrelationEngine(
            RuleRepository ruleRepository,
            IncidentService incidentService,
            LatencyBaselineService latencyBaselineService,
            ThreatIntelProvider threatIntelProvider,
            ObjectMapper objectMapper,
            Clock clock) {
        this.ruleRepository = ruleRepository;
        this.incidentService = incidentService;
        this.latencyBaselineService = latencyBaselineService;
        this.threatIntelProvider = threatIntelProvider;
        this.objectMapper = objectMapper;
        this.clock = clock;

        this.deviceStatusChanges = newCache();
        this.subnetOutages = newCache();
        this.sourceEvents = newCache();
        this.lastFired = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterWrite(Duration.ofDays(1))
                .build();
    }

    private <K, V> Cache<K, V> newCache() {
        return Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterWrite(STATE_RETENTION)
                .build();
    }

    // ---------------------------------------------------------------- rules

    /**
     * Replaces the in-memory rule set with the currently enabled rows.
     *
     * <p>Runs on a timer rather than per event on purpose: reading the rule
     * table on every incoming event would reintroduce exactly the synchronous
     * database round trip the in-memory windows exist to avoid. A rule edit
     * therefore takes effect within one refresh interval.
     */
    @Scheduled(fixedDelay = 30_000L)
    public void reloadRules() {
        try {
            List<Rule> rules = ruleRepository.findByEnabledTrue();
            List<CompiledRule> compiled = new ArrayList<>(rules.size());
            for (Rule rule : rules) {
                CompiledRule compiledRule = compile(rule);
                if (compiledRule != null) {
                    compiled.add(compiledRule);
                }
            }
            this.activeRules = List.copyOf(compiled);
        } catch (Exception ex) {
            // A failed refresh must not take correlation down; the previous
            // snapshot stays in force until the next attempt succeeds.
            log.warn("Correlation rule refresh failed, keeping previous rule set", ex);
        }
    }

    private CompiledRule compile(Rule rule) {
        try {
            JsonNode condition =
                    objectMapper.readTree(rule.getConditionJson() == null ? "{}" : rule.getConditionJson());
            JsonNode typeNode = condition.get("type");
            if (typeNode == null || typeNode.asText().isBlank()) {
                log.warn("Correlation rule {} has no condition type, skipping", rule.getId());
                return null;
            }
            return new CompiledRule(rule, typeNode.asText(), condition);
        } catch (Exception ex) {
            log.warn("Correlation rule {} has an unreadable condition, skipping", rule.getId(), ex);
            return null;
        }
    }

    /** Visible for tests: how many rules the engine is currently enforcing. */
    public int activeRuleCount() {
        return activeRules.size();
    }

    // ------------------------------------------------------------ device path

    /**
     * Feeds a device status change through every enabled rule.
     *
     * <p>Runs at the highest listener precedence so the latency comparison sees
     * the baseline as it stood before this sample; the baseline service updates
     * itself at the lowest precedence, after this method has returned.
     */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onDeviceEvent(DeviceStatusChangedEvent event) {
        Device device = event == null ? null : event.getDevice();
        if (device == null || device.getId() == null) {
            return;
        }

        long now = clock.millis();
        recordTimestamp(deviceStatusChanges, device.getId(), now);

        for (CompiledRule compiled : activeRules) {
            try {
                switch (compiled.type()) {
                    case TYPE_FLAP -> evaluateFlap(compiled, device, now);
                    case TYPE_SUBNET_OUTAGE -> evaluateSubnetOutage(compiled, device, event, now);
                    case TYPE_LATENCY_ANOMALY -> evaluateLatencyAnomaly(compiled, device, event, now);
                    default -> {
                        // Rule types that do not apply to device events.
                    }
                }
            } catch (Exception ex) {
                log.warn(
                        "Correlation rule {} failed on a device event",
                        compiled.rule().getId(),
                        ex);
            }
        }
    }

    private void evaluateFlap(CompiledRule compiled, Device device, long now) {
        Rule rule = compiled.rule();
        int occurrences = countWithin(deviceStatusChanges.getIfPresent(device.getId()), rule, now);
        if (occurrences < rule.getThresholdCount()) {
            return;
        }
        String cooldownKey = rule.getId() + "|device|" + device.getId();
        if (!claimFire(cooldownKey, rule, now)) {
            return;
        }
        raise(
                rule,
                "Device flapping: " + describe(device),
                "Rule '" + rule.getName() + "' matched: " + describe(device) + " changed status " + occurrences
                        + " times within " + rule.getWindowSeconds() + " seconds.",
                device.getId());
    }

    private void evaluateSubnetOutage(CompiledRule compiled, Device device, DeviceStatusChangedEvent event, long now) {
        Rule rule = compiled.rule();
        Set<String> downStatuses = downStatuses(compiled.condition());
        if (event.getNewStatus() == null || !downStatuses.contains(event.getNewStatus())) {
            return;
        }
        int octets = compiled.condition().path("prefixOctets").asInt(DEFAULT_PREFIX_OCTETS);
        String subnet = subnetPrefix(device.getIpAddress(), octets);
        if (subnet == null) {
            return;
        }

        String stateKey = rule.getId() + "|" + subnet;
        Map<Long, Long> downSince = subnetOutages.get(stateKey, key -> new ConcurrentHashMap<Long, Long>());
        downSince.put(device.getId(), now);

        long cutoff = now - windowMillis(rule);
        downSince.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        int affected = downSince.size();
        if (affected < rule.getThresholdCount()) {
            return;
        }
        if (!claimFire("outage|" + stateKey, rule, now)) {
            return;
        }
        raise(
                rule,
                "Possible uplink failure on " + subnet + "0/24",
                "Rule '" + rule.getName() + "' matched: " + affected + " devices on " + subnet
                        + "0/24 went down within " + rule.getWindowSeconds()
                        + " seconds, which points at shared infrastructure rather than at any one host.",
                device.getId());
    }

    private void evaluateLatencyAnomaly(
            CompiledRule compiled, Device device, DeviceStatusChangedEvent event, long now) {
        Rule rule = compiled.rule();
        Long latency = event.getLatency();
        if (latency == null || !latencyBaselineService.isAnomalous(device.getId(), latency)) {
            return;
        }
        if (!claimFire("latency|" + rule.getId() + "|" + device.getId(), rule, now)) {
            return;
        }
        String baselineText = latencyBaselineService
                .baselineFor(device.getId())
                .map(baseline -> String.format(
                        "baseline %.1f ms, deviation %.1f ms", baseline.mean(), baseline.standardDeviation()))
                .orElse("no baseline");
        raise(
                rule,
                "Latency anomaly: " + describe(device),
                "Rule '" + rule.getName() + "' matched: " + describe(device) + " reported " + latency
                        + " ms against its own " + baselineText + ".",
                device.getId());
    }

    // --------------------------------------------------------- ingestion path

    /**
     * Feeds an externally ingested event through the same rule evaluation the
     * device probe path uses, so a correlation is not limited to what this
     * service measures itself.
     */
    public void onIngestedEvent(IngestedEvent event) {
        if (event == null || event.getSource() == null) {
            return;
        }
        long now = clock.millis();

        checkReputation(event, now);

        for (CompiledRule compiled : activeRules) {
            if (!TYPE_EVENT_BURST.equals(compiled.type())) {
                continue;
            }
            try {
                evaluateEventBurst(compiled, event, now);
            } catch (Exception ex) {
                log.warn(
                        "Correlation rule {} failed on an ingested event",
                        compiled.rule().getId(),
                        ex);
            }
        }
    }

    private void checkReputation(IngestedEvent event, long now) {
        if (!threatIntelProvider.isKnownBad(event.getSource())) {
            return;
        }
        String key = "threatintel|" + event.getSource();
        Long previous = lastFired.getIfPresent(key);
        if (previous != null && now - previous < THREAT_INTEL_COOLDOWN.toMillis()) {
            return;
        }
        lastFired.put(key, now);
        incidentService.create(
                "Event from known-bad source " + event.getSource(),
                "Threat intelligence flagged " + event.getSource() + ", which submitted a '" + event.getCategory()
                        + "' event.",
                Severity.HIGH,
                null,
                null);
    }

    private void evaluateEventBurst(CompiledRule compiled, IngestedEvent event, long now) {
        Rule rule = compiled.rule();
        JsonNode categoryNode = compiled.condition().get("category");
        if (categoryNode != null && !categoryNode.asText().equals(event.getCategory())) {
            return;
        }
        String stateKey = rule.getId() + "|" + event.getSource();
        recordTimestamp(sourceEvents, stateKey, now);
        int occurrences = countWithin(sourceEvents.getIfPresent(stateKey), rule, now);
        if (occurrences < rule.getThresholdCount()) {
            return;
        }
        if (!claimFire("burst|" + stateKey, rule, now)) {
            return;
        }
        raise(
                rule,
                "Event burst from " + event.getSource(),
                "Rule '" + rule.getName() + "' matched: " + occurrences + " events from " + event.getSource()
                        + " within " + rule.getWindowSeconds() + " seconds.",
                null);
    }

    // ------------------------------------------------------------- internals

    private <K> void recordTimestamp(Cache<K, Deque<Long>> cache, K key, long now) {
        Deque<Long> timestamps = cache.get(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(now);
            while (timestamps.size() > MAX_TIMESTAMPS_PER_KEY) {
                timestamps.removeFirst();
            }
        }
        // Re-insert so the entry's write-expiry reflects the latest activity.
        cache.put(key, timestamps);
    }

    private int countWithin(Deque<Long> timestamps, Rule rule, long now) {
        if (timestamps == null) {
            return 0;
        }
        long cutoff = now - windowMillis(rule);
        int count = 0;
        synchronized (timestamps) {
            for (Long timestamp : timestamps) {
                if (timestamp >= cutoff) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Marks a key as having fired, unless it already fired inside this rule's
     * window. Without this a rule that matches at the third event would match
     * again at the fourth, fifth and sixth, burying the analyst in duplicates
     * of the same finding.
     *
     * @return true when the caller may raise an incident
     */
    private boolean claimFire(String key, Rule rule, long now) {
        Long previous = lastFired.getIfPresent(key);
        if (previous != null && now - previous < windowMillis(rule)) {
            return false;
        }
        lastFired.put(key, now);
        return true;
    }

    private Incident raise(Rule rule, String title, String description, Long sourceDeviceId) {
        Severity severity = rule.getSeverity() == null ? Severity.MEDIUM : rule.getSeverity();
        return incidentService.create(title, description, severity, sourceDeviceId, null);
    }

    private long windowMillis(Rule rule) {
        return Math.max(1L, (long) rule.getWindowSeconds()) * 1000L;
    }

    private Set<String> downStatuses(JsonNode condition) {
        JsonNode node = condition.get("downStatuses");
        if (node == null || !node.isArray() || node.isEmpty()) {
            return DEFAULT_DOWN_STATUSES;
        }
        Set<String> statuses = new HashSet<>();
        node.forEach(element -> statuses.add(element.asText()));
        return statuses;
    }

    /**
     * @return the first {@code octets} octets of an IPv4 address including the
     *         trailing dot, or null when the address is not usable
     */
    static String subnetPrefix(String ipAddress, int octets) {
        if (ipAddress == null || octets < 1) {
            return null;
        }
        String[] parts = ipAddress.trim().split("\\.");
        if (parts.length < octets) {
            return null;
        }
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < octets; i++) {
            if (parts[i].isEmpty()) {
                return null;
            }
            prefix.append(parts[i]).append('.');
        }
        return prefix.toString();
    }

    private String describe(Device device) {
        String name = device.getName() == null ? "device " + device.getId() : device.getName();
        return name + " [" + device.getIpAddress() + "]";
    }

    /** Visible for tests: current in-memory window sizes, for assertions. */
    Map<String, Integer> stateSnapshot() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        snapshot.put("deviceStatusChanges", deviceStatusChanges.asMap().size());
        snapshot.put("subnetOutages", subnetOutages.asMap().size());
        snapshot.put("sourceEvents", sourceEvents.asMap().size());
        return snapshot;
    }

    /**
     * A rule with its condition already parsed, so evaluation never touches the
     * JSON text.
     */
    record CompiledRule(Rule rule, String type, JsonNode condition) {}
}

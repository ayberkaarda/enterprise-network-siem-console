package com.example.demo.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.anomaly.LatencyBaselineService;
import com.example.demo.correlation.threatintel.LocalBlocklistProvider;
import com.example.demo.correlation.threatintel.ThreatIntelProvider;
import com.example.demo.device.Device;
import com.example.demo.event.DeviceStatusChangedEvent;
import com.example.demo.incident.IncidentService;
import com.example.demo.incident.Severity;
import com.example.demo.ingestion.IngestedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/**
 * Behavioural tests for the correlation engine.
 *
 * <p>Every case drives the engine with a hand-controlled clock and asserts on
 * the incidents it asks for, including the near-miss cases: a detection that
 * fires one event early is as wrong as one that never fires.
 */
class CorrelationEngineTest {

    private static final Instant START = Instant.parse("2026-09-14T10:00:00Z");

    private RuleRepository ruleRepository;
    private IncidentService incidentService;
    private LatencyBaselineService latencyBaselineService;
    private ThreatIntelProvider threatIntelProvider;
    private MutableClock clock;
    private CorrelationEngine engine;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(RuleRepository.class);
        incidentService = mock(IncidentService.class);
        latencyBaselineService = new LatencyBaselineService();
        threatIntelProvider = new LocalBlocklistProvider(List.of("192.0.2.66", "203.0.113."));
        clock = new MutableClock(START);
        engine = new CorrelationEngine(
                ruleRepository,
                incidentService,
                latencyBaselineService,
                threatIntelProvider,
                new ObjectMapper(),
                clock);
    }

    // ------------------------------------------------------------------ flap

    @Test
    void flapRuleFiresOnTheThirdStatusChangeWithinTheWindow() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));
        Device device = device(7L, "core-switch", "10.0.5.11");

        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
        clock.advance(Duration.ofSeconds(30));
        engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());

        clock.advance(Duration.ofSeconds(30));
        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(incidentService, times(1))
                .create(title.capture(), description.capture(), eq(Severity.MEDIUM), eq(7L), any());

        assertThat(title.getValue()).contains("core-switch");
        assertThat(description.getValue())
                .contains("Device flapping")
                .contains("3 times")
                .contains("300 seconds");
    }

    @Test
    void flapRuleDoesNotFireOneEventShortOfTheThreshold() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));
        Device device = device(7L, "core-switch", "10.0.5.11");

        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
        clock.advance(Duration.ofSeconds(60));
        engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void flapRuleIgnoresChangesThatFellOutOfTheWindow() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));
        Device device = device(7L, "core-switch", "10.0.5.11");

        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
        engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));

        // Both earlier changes age out before the next pair arrives.
        clock.advance(Duration.ofSeconds(301));

        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
        engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void flapRuleDoesNotRepeatItselfWithinTheSameWindow() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));
        Device device = device(7L, "core-switch", "10.0.5.11");

        for (int i = 0; i < 8; i++) {
            engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
            clock.advance(Duration.ofSeconds(5));
        }

        verify(incidentService, times(1)).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void disabledRulesAreNeverLoaded() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of());
        engine.reloadRules();

        Device device = device(7L, "core-switch", "10.0.5.11");
        for (int i = 0; i < 5; i++) {
            engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
        }

        assertThat(engine.activeRuleCount()).isZero();
        verifyNoMoreInteractions(incidentService);
    }

    // -------------------------------------------------------- subnet outage

    @Test
    void subnetOutageRuleFiresWhenThreeHostsOnTheSamePrefixGoDown() {
        loadRules(rule(
                2L,
                "Simultaneous subnet outage",
                "{\"type\":\"subnet_outage\",\"downStatuses\":[\"INACTIVE\"],\"prefixOctets\":3}",
                3,
                120,
                Severity.HIGH));

        engine.onDeviceEvent(statusChange(device(11L, "rack-a", "10.0.5.11"), "ACTIVE", "INACTIVE", -1L));
        clock.advance(Duration.ofSeconds(10));
        engine.onDeviceEvent(statusChange(device(12L, "rack-b", "10.0.5.12"), "ACTIVE", "INACTIVE", -1L));

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());

        clock.advance(Duration.ofSeconds(10));
        engine.onDeviceEvent(statusChange(device(13L, "rack-c", "10.0.5.13"), "ACTIVE", "INACTIVE", -1L));

        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(incidentService, times(1)).create(anyString(), description.capture(), eq(Severity.HIGH), any(), any());
        assertThat(description.getValue()).contains("10.0.5.0/24").contains("3 devices");
    }

    @Test
    void subnetOutageRuleIgnoresHostsOnOtherPrefixes() {
        loadRules(rule(
                2L,
                "Simultaneous subnet outage",
                "{\"type\":\"subnet_outage\",\"downStatuses\":[\"INACTIVE\"],\"prefixOctets\":3}",
                3,
                120,
                Severity.HIGH));

        engine.onDeviceEvent(statusChange(device(11L, "rack-a", "10.0.5.11"), "ACTIVE", "INACTIVE", -1L));
        engine.onDeviceEvent(statusChange(device(21L, "other-a", "10.0.6.21"), "ACTIVE", "INACTIVE", -1L));
        engine.onDeviceEvent(statusChange(device(31L, "other-b", "10.0.7.31"), "ACTIVE", "INACTIVE", -1L));

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void subnetOutageRuleIgnoresHostsComingBackUp() {
        loadRules(rule(
                2L,
                "Simultaneous subnet outage",
                "{\"type\":\"subnet_outage\",\"downStatuses\":[\"INACTIVE\"],\"prefixOctets\":3}",
                3,
                120,
                Severity.HIGH));

        engine.onDeviceEvent(statusChange(device(11L, "rack-a", "10.0.5.11"), "INACTIVE", "ACTIVE", 8L));
        engine.onDeviceEvent(statusChange(device(12L, "rack-b", "10.0.5.12"), "INACTIVE", "ACTIVE", 8L));
        engine.onDeviceEvent(statusChange(device(13L, "rack-c", "10.0.5.13"), "INACTIVE", "ACTIVE", 8L));

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    // ------------------------------------------------------ latency anomaly

    @Test
    void latencyRuleFiresOnlyOnceTheDeviceHasABaselineToDeviateFrom() {
        loadRules(
                rule(3L, "Latency beyond device baseline", "{\"type\":\"latency_anomaly\"}", 1, 300, Severity.MEDIUM));
        Device device = device(9L, "db-primary", "10.0.9.4");

        // Not enough history yet: a spike here is not yet a claim about anything.
        engine.onDeviceEvent(statusChange(device, "ACTIVE", "ACTIVE", 900L));
        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());

        latencyBaselineService.reset();
        for (int i = 0; i < 10; i++) {
            latencyBaselineService.record(9L, 20L);
        }

        engine.onDeviceEvent(statusChange(device, "ACTIVE", "ACTIVE", 22L));
        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());

        engine.onDeviceEvent(statusChange(device, "ACTIVE", "ACTIVE", 900L));

        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(incidentService, times(1))
                .create(anyString(), description.capture(), eq(Severity.MEDIUM), eq(9L), any());
        assertThat(description.getValue()).contains("900 ms").contains("baseline");
    }

    // --------------------------------------------------------- ingested path

    @Test
    void ingestedEventFromKnownBadSourceOpensAnIncidentOnce() {
        loadRules();

        engine.onIngestedEvent(ingested("192.0.2.66", "AUTH", Severity.MEDIUM));
        engine.onIngestedEvent(ingested("192.0.2.66", "AUTH", Severity.MEDIUM));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(incidentService, times(1)).create(title.capture(), anyString(), eq(Severity.HIGH), any(), any());
        assertThat(title.getValue()).contains("192.0.2.66");
    }

    @Test
    void ingestedEventFromCleanSourceOpensNothing() {
        loadRules();

        engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.MEDIUM));

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void eventBurstRuleFiresOnTheThirdEventFromTheSameSource() {
        loadRules(rule(
                4L, "Authentication burst", "{\"type\":\"event_burst\",\"category\":\"AUTH\"}", 3, 60, Severity.HIGH));

        engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW));
        engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW));
        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());

        engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW));

        verify(incidentService, times(1)).create(anyString(), anyString(), eq(Severity.HIGH), any(), any());
    }

    @Test
    void eventBurstRuleIgnoresOtherCategoriesAndOtherSources() {
        loadRules(rule(
                4L, "Authentication burst", "{\"type\":\"event_burst\",\"category\":\"AUTH\"}", 3, 60, Severity.HIGH));

        engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW));
        engine.onIngestedEvent(ingested("10.0.0.15", "NETFLOW", Severity.LOW));
        engine.onIngestedEvent(ingested("10.0.0.16", "AUTH", Severity.LOW));
        engine.onIngestedEvent(ingested("10.0.0.17", "AUTH", Severity.LOW));

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    // ------------------------------------------------------------ hot path

    @Test
    void rulesAreReadOnceAndNotPerEvent() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));
        Device device = device(7L, "core-switch", "10.0.5.11");

        for (int i = 0; i < 50; i++) {
            engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
            clock.advance(Duration.ofSeconds(1));
        }

        verify(ruleRepository, times(1)).findByEnabledTrue();
        verifyNoMoreInteractions(ruleRepository);
    }

    @Test
    void unreadableConditionIsSkippedRatherThanTakingTheEngineDown() {
        loadRules(
                rule(1L, "Broken", "not json at all", 3, 300, Severity.MEDIUM),
                rule(2L, "Device flapping", "{\"type\":\"flap\"}", 2, 300, Severity.LOW));

        assertThat(engine.activeRuleCount()).isEqualTo(1);

        Device device = device(7L, "core-switch", "10.0.5.11");
        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
        engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));

        verify(incidentService, times(1)).create(anyString(), anyString(), eq(Severity.LOW), any(), any());
    }

    @Test
    void aFailingRefreshKeepsThePreviousRuleSet() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));
        assertThat(engine.activeRuleCount()).isEqualTo(1);

        when(ruleRepository.findByEnabledTrue()).thenThrow(new IllegalStateException("database is away"));
        engine.reloadRules();

        assertThat(engine.activeRuleCount()).isEqualTo(1);
    }

    @Test
    void subnetPrefixHandlesRealAndMalformedAddresses() {
        assertThat(CorrelationEngine.subnetPrefix("10.0.5.11", 3)).isEqualTo("10.0.5.");
        assertThat(CorrelationEngine.subnetPrefix("10.0.5.11", 2)).isEqualTo("10.0.");
        assertThat(CorrelationEngine.subnetPrefix("10.0", 3)).isNull();
        assertThat(CorrelationEngine.subnetPrefix(null, 3)).isNull();
    }

    @Test
    void subnetPrefixRejectsZeroOctetsAndEmptySegments() {
        assertThat(CorrelationEngine.subnetPrefix("10.0.5.11", 0)).isNull();
        assertThat(CorrelationEngine.subnetPrefix("10..5.11", 3)).isNull();
    }

    // ------------------------------------------------------ rule compilation

    @Test
    void rulesWithNullMissingOrBlankConditionTypeAreNeverActivated() {
        loadRules(
                rule(1L, "No condition at all", null, 1, 60, Severity.LOW),
                rule(2L, "No type key", "{}", 1, 60, Severity.LOW),
                rule(3L, "Blank type", "{\"type\":\"\"}", 1, 60, Severity.LOW));

        assertThat(engine.activeRuleCount()).isZero();
    }

    // ---------------------------------------------------------- device path

    @Test
    void onDeviceEventIgnoresANullEventAndADeviceWithoutAnId() {
        assertThatCode(() -> engine.onDeviceEvent(null)).doesNotThrowAnyException();

        Device deviceWithoutId = device(null, "unregistered", "10.0.0.99");
        engine.onDeviceEvent(statusChange(deviceWithoutId, "UNKNOWN", "ACTIVE", 5L));

        verifyNoInteractions(incidentService);
    }

    @Test
    void aFailingRuleEvaluationIsLoggedAndDoesNotStopTheOtherRules() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));
        when(incidentService.create(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new IllegalStateException("incident store unavailable"));
        Device device = device(7L, "core-switch", "10.0.5.11");

        assertThatCode(() -> {
                    engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
                    clock.advance(Duration.ofSeconds(30));
                    engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));
                    clock.advance(Duration.ofSeconds(30));
                    engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
                })
                .doesNotThrowAnyException();
    }

    @Test
    void describeFallsBackToTheDeviceIdWhenTheNameIsMissing() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));
        Device device = device(7L, null, "10.0.5.11");

        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
        clock.advance(Duration.ofSeconds(30));
        engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));
        clock.advance(Duration.ofSeconds(30));
        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(incidentService, times(1)).create(title.capture(), anyString(), any(), eq(7L), any());
        assertThat(title.getValue()).contains("device 7");
    }

    @Test
    void aRuleWithoutAnExplicitSeverityDefaultsToMedium() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, null));
        Device device = device(7L, "core-switch", "10.0.5.11");

        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
        clock.advance(Duration.ofSeconds(30));
        engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));
        clock.advance(Duration.ofSeconds(30));
        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));

        verify(incidentService, times(1)).create(anyString(), anyString(), eq(Severity.MEDIUM), eq(7L), any());
    }

    @Test
    void aRepeatedFlapIsAllowedToFireAgainOnceTheWindowHasFullyElapsed() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));
        Device device = device(7L, "core-switch", "10.0.5.11");

        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
        clock.advance(Duration.ofSeconds(30));
        engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));
        clock.advance(Duration.ofSeconds(30));
        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));

        clock.advance(Duration.ofSeconds(301));
        engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));
        clock.advance(Duration.ofSeconds(30));
        engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
        clock.advance(Duration.ofSeconds(30));
        engine.onDeviceEvent(statusChange(device, "INACTIVE", "ACTIVE", 12L));

        verify(incidentService, times(2)).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void flapWindowNeverTracksMoreThanTheCapForAVeryChattyDevice() {
        // threshold set above the tracked-timestamp cap: if the window did not
        // trim old entries the count would exceed it and the rule would fire.
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 130, 1_000, Severity.MEDIUM));
        Device device = device(7L, "core-switch", "10.0.5.11");

        for (int i = 0; i < 150; i++) {
            engine.onDeviceEvent(statusChange(device, "ACTIVE", "INACTIVE", 12L));
            clock.advance(Duration.ofSeconds(1));
        }

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    // -------------------------------------------------- subnet outage extras

    @Test
    void subnetOutageIgnoresADeviceWithAnUnparseableIpAddress() {
        loadRules(rule(
                2L,
                "Simultaneous subnet outage",
                "{\"type\":\"subnet_outage\",\"downStatuses\":[\"INACTIVE\"],\"prefixOctets\":3}",
                1,
                120,
                Severity.HIGH));

        engine.onDeviceEvent(statusChange(device(41L, "unaddressed", "not-an-ip"), "ACTIVE", "INACTIVE", -1L));

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void subnetOutageAgesOutHostsThatWentDownOutsideTheWindow() {
        loadRules(rule(
                2L,
                "Simultaneous subnet outage",
                "{\"type\":\"subnet_outage\",\"downStatuses\":[\"INACTIVE\"],\"prefixOctets\":3}",
                3,
                120,
                Severity.HIGH));

        engine.onDeviceEvent(statusChange(device(11L, "rack-a", "10.0.5.11"), "ACTIVE", "INACTIVE", -1L));
        clock.advance(Duration.ofSeconds(130));
        engine.onDeviceEvent(statusChange(device(12L, "rack-b", "10.0.5.12"), "ACTIVE", "INACTIVE", -1L));
        clock.advance(Duration.ofSeconds(10));
        engine.onDeviceEvent(statusChange(device(13L, "rack-c", "10.0.5.13"), "ACTIVE", "INACTIVE", -1L));

        // rack-a aged out of the 120s window, so only two hosts are down: below threshold.
        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void subnetOutageDoesNotRepeatWithinTheSameWindow() {
        loadRules(rule(
                2L,
                "Simultaneous subnet outage",
                "{\"type\":\"subnet_outage\",\"downStatuses\":[\"INACTIVE\"],\"prefixOctets\":3}",
                3,
                120,
                Severity.HIGH));

        engine.onDeviceEvent(statusChange(device(11L, "rack-a", "10.0.5.11"), "ACTIVE", "INACTIVE", -1L));
        engine.onDeviceEvent(statusChange(device(12L, "rack-b", "10.0.5.12"), "ACTIVE", "INACTIVE", -1L));
        engine.onDeviceEvent(statusChange(device(13L, "rack-c", "10.0.5.13"), "ACTIVE", "INACTIVE", -1L));

        clock.advance(Duration.ofSeconds(5));
        engine.onDeviceEvent(statusChange(device(14L, "rack-d", "10.0.5.14"), "ACTIVE", "INACTIVE", -1L));
        engine.onDeviceEvent(statusChange(device(15L, "rack-e", "10.0.5.15"), "ACTIVE", "INACTIVE", -1L));
        engine.onDeviceEvent(statusChange(device(16L, "rack-f", "10.0.5.16"), "ACTIVE", "INACTIVE", -1L));

        verify(incidentService, times(1)).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void subnetOutageFallsBackToTheDefaultDownStatusWhenTheConditionOmitsIt() {
        loadRules(rule(2L, "Simultaneous subnet outage", "{\"type\":\"subnet_outage\",\"prefixOctets\":3}", 1, 120, Severity.HIGH));

        engine.onDeviceEvent(statusChange(device(11L, "rack-a", "10.0.5.11"), "ACTIVE", "INACTIVE", -1L));

        verify(incidentService, times(1)).create(anyString(), anyString(), eq(Severity.HIGH), any(), any());
    }

    // ------------------------------------------------- latency anomaly extras

    @Test
    void latencyAnomalyIgnoresAReadingWithoutALatencyValue() {
        loadRules(
                rule(3L, "Latency beyond device baseline", "{\"type\":\"latency_anomaly\"}", 1, 300, Severity.MEDIUM));
        Device device = device(9L, "db-primary", "10.0.9.4");
        for (int i = 0; i < 10; i++) {
            latencyBaselineService.record(9L, 20L);
        }

        engine.onDeviceEvent(new DeviceStatusChangedEvent(device, "ACTIVE", "ACTIVE", null));

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void latencyAnomalyDoesNotRepeatWithinTheSameWindow() {
        loadRules(
                rule(3L, "Latency beyond device baseline", "{\"type\":\"latency_anomaly\"}", 1, 300, Severity.MEDIUM));
        Device device = device(9L, "db-primary", "10.0.9.4");
        for (int i = 0; i < 10; i++) {
            latencyBaselineService.record(9L, 20L);
        }

        engine.onDeviceEvent(statusChange(device, "ACTIVE", "ACTIVE", 900L));
        clock.advance(Duration.ofSeconds(10));
        engine.onDeviceEvent(statusChange(device, "ACTIVE", "ACTIVE", 950L));

        verify(incidentService, times(1)).create(anyString(), anyString(), any(), eq(9L), any());
    }

    // --------------------------------------------------------- ingested path extras

    @Test
    void onIngestedEventIgnoresANullEventAndAnEventWithoutASource() {
        assertThatCode(() -> engine.onIngestedEvent(null)).doesNotThrowAnyException();

        engine.onIngestedEvent(ingested(null, "AUTH", Severity.MEDIUM));

        verifyNoInteractions(incidentService);
    }

    @Test
    void onIngestedEventSkipsRulesThatAreNotEventBurstRules() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));

        assertThatCode(() -> engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW)))
                .doesNotThrowAnyException();

        verify(incidentService, never()).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void aFailingEventBurstEvaluationIsLoggedAndDoesNotPropagate() {
        loadRules(rule(
                4L, "Authentication burst", "{\"type\":\"event_burst\",\"category\":\"AUTH\"}", 1, 60, Severity.HIGH));
        when(incidentService.create(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new IllegalStateException("incident store unavailable"));

        assertThatCode(() -> engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW)))
                .doesNotThrowAnyException();
    }

    @Test
    void eventBurstRuleWithoutACategoryFilterMatchesAnyCategory() {
        loadRules(rule(4L, "Any-category burst", "{\"type\":\"event_burst\"}", 2, 60, Severity.HIGH));

        engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW));
        engine.onIngestedEvent(ingested("10.0.0.15", "NETFLOW", Severity.LOW));

        verify(incidentService, times(1)).create(anyString(), anyString(), eq(Severity.HIGH), any(), any());
    }

    @Test
    void eventBurstRuleDoesNotRepeatWithinTheSameWindow() {
        loadRules(rule(
                4L, "Authentication burst", "{\"type\":\"event_burst\",\"category\":\"AUTH\"}", 2, 60, Severity.HIGH));

        engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW));
        engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW));
        clock.advance(Duration.ofSeconds(5));
        engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW));
        engine.onIngestedEvent(ingested("10.0.0.15", "AUTH", Severity.LOW));

        verify(incidentService, times(1)).create(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void threatIntelHitsAreAllowedToFireAgainAfterTheCooldownExpires() {
        loadRules();

        engine.onIngestedEvent(ingested("192.0.2.66", "AUTH", Severity.MEDIUM));
        clock.advance(Duration.ofMinutes(16));
        engine.onIngestedEvent(ingested("192.0.2.66", "AUTH", Severity.MEDIUM));

        verify(incidentService, times(2)).create(anyString(), anyString(), eq(Severity.HIGH), any(), any());
    }

    // ------------------------------------------------------------ diagnostics

    @Test
    void stateSnapshotReflectsInMemoryWindowActivity() {
        loadRules(rule(1L, "Device flapping", "{\"type\":\"flap\"}", 3, 300, Severity.MEDIUM));
        engine.onDeviceEvent(statusChange(device(7L, "core-switch", "10.0.5.11"), "ACTIVE", "INACTIVE", 12L));

        Map<String, Integer> snapshot = engine.stateSnapshot();

        assertThat(snapshot).containsKeys("deviceStatusChanges", "subnetOutages", "sourceEvents");
        assertThat(snapshot.get("deviceStatusChanges")).isEqualTo(1);
    }

    // ------------------------------------------------------------- fixtures

    private void loadRules(Rule... rules) {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rules));
        engine.reloadRules();
    }

    private static Rule rule(
            Long id, String name, String conditionJson, int thresholdCount, int windowSeconds, Severity severity) {
        Rule rule = new Rule();
        rule.setId(id);
        rule.setName(name);
        rule.setEnabled(true);
        rule.setConditionJson(conditionJson);
        rule.setThresholdCount(thresholdCount);
        rule.setWindowSeconds(windowSeconds);
        rule.setSeverity(severity);
        rule.setCreatedAt(START);
        return rule;
    }

    private static Device device(Long id, String name, String ipAddress) {
        Device device = new Device();
        device.setId(id);
        device.setName(name);
        device.setIpAddress(ipAddress);
        device.setDeviceType("SWITCH");
        return device;
    }

    private static DeviceStatusChangedEvent statusChange(Device device, String from, String to, long latency) {
        return new DeviceStatusChangedEvent(device, from, to, latency);
    }

    private IngestedEvent ingested(String source, String category, Severity severity) {
        IngestedEvent event = new IngestedEvent();
        event.setSource(source);
        event.setCategory(category);
        event.setSeverity(severity);
        event.setRawPayload("{}");
        event.setOccurredAt(clock.instant());
        event.setReceivedAt(clock.instant());
        return event;
    }
}

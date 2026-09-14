package com.example.demo.correlation.threatintel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalBlocklistProviderTest {

    @Test
    void exactEntriesMatchOnlyThemselves() {
        ThreatIntelProvider provider = new LocalBlocklistProvider(List.of("192.0.2.66"));

        assertThat(provider.isKnownBad("192.0.2.66")).isTrue();
        assertThat(provider.isKnownBad("192.0.2.67")).isFalse();
    }

    @Test
    void trailingDotEntriesMatchEverythingUnderThem() {
        ThreatIntelProvider provider = new LocalBlocklistProvider(List.of("203.0.113."));

        assertThat(provider.isKnownBad("203.0.113.1")).isTrue();
        assertThat(provider.isKnownBad("203.0.113.250")).isTrue();
        assertThat(provider.isKnownBad("203.0.114.1")).isFalse();
    }

    @Test
    void blankAndNullInputIsNeverAMatch() {
        ThreatIntelProvider provider = new LocalBlocklistProvider(List.of("192.0.2.66"));

        assertThat(provider.isKnownBad(null)).isFalse();
        assertThat(provider.isKnownBad("")).isFalse();
        assertThat(provider.isKnownBad("   ")).isFalse();
    }

    @Test
    void emptyAndPaddedEntriesAreDiscardedRatherThanMatchingEverything() {
        LocalBlocklistProvider provider =
                new LocalBlocklistProvider(Arrays.asList("  192.0.2.66  ", "", "   ", null));

        assertThat(provider.size()).isEqualTo(1);
        assertThat(provider.isKnownBad("192.0.2.66")).isTrue();
        assertThat(provider.isKnownBad("8.8.8.8")).isFalse();
    }

    @Test
    void anEmptyListBlocksNothing() {
        LocalBlocklistProvider provider = new LocalBlocklistProvider(List.of());

        assertThat(provider.size()).isZero();
        assertThat(provider.isKnownBad("192.0.2.66")).isFalse();
    }
}

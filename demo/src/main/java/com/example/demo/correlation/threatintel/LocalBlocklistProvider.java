package com.example.demo.correlation.threatintel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reputation lookup backed by a locally configured blocklist.
 *
 * <p>The list is read once at startup from {@code siem.threat-intel.blocklist}
 * (comma separated) and held in an immutable set, so a lookup is a hash probe
 * with no allocation and no I/O — the event hot path can call it per event.
 *
 * <p>An entry is either a full address ({@code 192.0.2.66}) or a prefix ending
 * in a dot ({@code 203.0.113.}), which matches every address underneath it.
 * That is enough to express the per-address and per-/24 entries a real feed
 * hands out, without pulling in CIDR arithmetic for a list this small.
 *
 * <p>The shipped defaults are addresses from the ranges reserved for
 * documentation, so nothing real is ever flagged by accident; they stand in for
 * a subscription feed until one is wired up behind the same interface.
 */
@Component
public class LocalBlocklistProvider implements ThreatIntelProvider {

    private final Set<String> exactMatches;
    private final List<String> prefixes;

    public LocalBlocklistProvider(
            @Value("${siem.threat-intel.blocklist:192.0.2.66,198.51.100.14,203.0.113.7,203.0.113.}")
            List<String> blocklist) {
        Set<String> exact = new LinkedHashSet<>();
        List<String> prefixList = new ArrayList<>();
        if (blocklist != null) {
            for (String rawEntry : blocklist) {
                if (rawEntry == null) {
                    continue;
                }
                String entry = rawEntry.trim();
                if (entry.isEmpty()) {
                    continue;
                }
                if (entry.endsWith(".")) {
                    prefixList.add(entry);
                } else {
                    exact.add(entry);
                }
            }
        }
        this.exactMatches = Set.copyOf(exact);
        this.prefixes = List.copyOf(prefixList);
    }

    @Override
    public boolean isKnownBad(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }
        String candidate = ipAddress.trim();
        if (exactMatches.contains(candidate)) {
            return true;
        }
        for (String prefix : prefixes) {
            if (candidate.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** @return how many entries the provider is currently enforcing */
    public int size() {
        return exactMatches.size() + prefixes.size();
    }
}

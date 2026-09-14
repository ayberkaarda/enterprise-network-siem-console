package com.example.demo.correlation.threatintel;

/**
 * Reputation lookup for an address seen in traffic or in an ingested event.
 *
 * <p>The abstraction exists so that the only place that ever talks to an
 * external reputation feed is an implementation of this interface. Services and
 * controllers depend on the interface; none of them know whether the answer
 * came from a local list, a cached feed or a remote API, and none of them may
 * make that call themselves.
 *
 * <p>Implementations are consulted on the event hot path and must therefore
 * answer from local state. A blocking network call belongs behind a refresh
 * job that updates that state, never inside {@link #isKnownBad(String)}.
 */
public interface ThreatIntelProvider {

    /**
     * @param ipAddress address to look up; null or blank input is not a match
     * @return true when the address is known to be malicious
     */
    boolean isKnownBad(String ipAddress);
}

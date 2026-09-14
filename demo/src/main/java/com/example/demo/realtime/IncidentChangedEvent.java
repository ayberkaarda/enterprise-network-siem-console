package com.example.demo.realtime;

/**
 * Internal notification that an incident was opened or moved to a new state.
 *
 * <p>It carries the finished outbound payload rather than the entity. The
 * payload is assembled by the publisher, inside the transaction that wrote the
 * row; the listener then has nothing left to read, which is what makes it safe
 * to run after the transaction has closed and on another thread.
 */
public record IncidentChangedEvent(IncidentRealtimeEvent payload) {}

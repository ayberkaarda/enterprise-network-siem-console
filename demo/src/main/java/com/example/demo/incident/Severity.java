package com.example.demo.incident;

/**
 * Ordered impact scale shared by incidents, correlation rules and ingested
 * events. The declaration order is the escalation order, so {@code compareTo}
 * can be used to answer "is this at least HIGH?".
 */
public enum Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

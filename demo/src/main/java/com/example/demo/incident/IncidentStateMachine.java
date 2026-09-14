package com.example.demo.incident;

import com.example.demo.common.IllegalStateTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the incident lifecycle. Analysts may not skip
 * steps: an incident has to be acknowledged before work starts and resolved
 * before it is closed, which is what makes the audit trail meaningful. Reopening
 * a resolved incident is allowed, because verification sometimes fails; a closed
 * incident is terminal.
 *
 * <p>Kept as a stateless utility rather than a bean so the rules can be unit
 * tested without a Spring context and reused from anywhere.
 */
public final class IncidentStateMachine {

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED = buildAllowedTransitions();

    private IncidentStateMachine() {}

    private static Map<IncidentStatus, Set<IncidentStatus>> buildAllowedTransitions() {
        Map<IncidentStatus, Set<IncidentStatus>> allowed = new EnumMap<>(IncidentStatus.class);
        allowed.put(IncidentStatus.OPEN, EnumSet.of(IncidentStatus.ACKNOWLEDGED));
        allowed.put(IncidentStatus.ACKNOWLEDGED, EnumSet.of(IncidentStatus.IN_PROGRESS));
        allowed.put(IncidentStatus.IN_PROGRESS, EnumSet.of(IncidentStatus.RESOLVED));
        allowed.put(IncidentStatus.RESOLVED, EnumSet.of(IncidentStatus.CLOSED, IncidentStatus.IN_PROGRESS));
        allowed.put(IncidentStatus.CLOSED, EnumSet.noneOf(IncidentStatus.class));
        return allowed;
    }

    /**
     * @return the states reachable in one step from {@code from}
     */
    public static Set<IncidentStatus> allowedTargets(IncidentStatus from) {
        if (from == null) {
            return EnumSet.noneOf(IncidentStatus.class);
        }
        return EnumSet.copyOf(ALLOWED.getOrDefault(from, EnumSet.noneOf(IncidentStatus.class)));
    }

    public static boolean canTransition(IncidentStatus from, IncidentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(IncidentStatus.class)).contains(to);
    }

    /**
     * Verifies a transition, throwing when it is not part of the lifecycle.
     *
     * @throws IllegalStateTransitionException if the edge does not exist
     */
    public static void validateTransition(IncidentStatus from, IncidentStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException("Cannot transition incident from " + from + " to " + to);
        }
    }
}

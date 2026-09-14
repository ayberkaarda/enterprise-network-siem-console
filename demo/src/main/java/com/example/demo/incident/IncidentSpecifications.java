package com.example.demo.incident;

import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable {@link Specification} building blocks for dynamic incident queries.
 * Every factory returns an unrestricted specification when its criterion is
 * absent, so callers can chain them unconditionally.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {}

    public static Specification<Incident> hasStatus(IncidentStatus status) {
        if (status == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Incident> hasSeverity(Severity severity) {
        if (severity == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("severity"), severity);
    }

    public static Specification<Incident> hasSourceDeviceId(Long sourceDeviceId) {
        if (sourceDeviceId == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("sourceDeviceId"), sourceDeviceId);
    }

    /**
     * Combines every supported incident filter; null criteria are skipped.
     */
    public static Specification<Incident> filterBy(IncidentStatus status, Severity severity, Long sourceDeviceId) {
        return Specification.where(hasStatus(status)).and(hasSeverity(severity)).and(hasSourceDeviceId(sourceDeviceId));
    }
}

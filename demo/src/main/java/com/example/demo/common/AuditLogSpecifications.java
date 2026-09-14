package com.example.demo.common;

import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable {@link Specification} building blocks for dynamic audit log queries.
 * Every factory returns an unrestricted specification when its criterion is
 * absent, so callers can chain them unconditionally.
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    public static Specification<AuditLog> timestampBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return Specification.unrestricted();
        }
        if (from != null && to != null) {
            return (root, query, cb) -> cb.between(root.get("timestamp"), from, to);
        }
        if (from != null) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from);
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }
}

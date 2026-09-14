package com.example.demo.device;

import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable {@link Specification} building blocks for dynamic device queries.
 * Every factory returns an unrestricted specification when its criterion is
 * absent, so callers can chain them unconditionally and absent filters simply
 * drop out of the generated predicate.
 */
public final class DeviceSpecifications {

    private DeviceSpecifications() {
    }

    public static Specification<Device> hasStatus(String status) {
        if (!hasText(status)) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Device> hasType(String deviceType) {
        if (!hasText(deviceType)) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("deviceType"), deviceType);
    }

    public static Specification<Device> ipPrefixStartsWith(String ipPrefix) {
        if (!hasText(ipPrefix)) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.like(root.get("ipAddress"), ipPrefix + "%");
    }

    /**
     * Combines every supported device filter; null or blank criteria are skipped.
     */
    public static Specification<Device> filterBy(String status, String deviceType, String ipPrefix) {
        return Specification.where(hasStatus(status))
                .and(hasType(deviceType))
                .and(ipPrefixStartsWith(ipPrefix));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

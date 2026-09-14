package com.example.demo.common;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves who an audit entry should be attributed to.
 *
 * <p>Most audit entries are written from inside an HTTP request, where
 * {@code JwtAuthenticationFilter} has already put the caller's username in the
 * security context by the time a service method runs. The scheduled network
 * sweep is not a request at all — it is a timer firing on its own thread — so
 * there is no context to read there, and that absence is reported as
 * {@code "system"} rather than left blank, so every row in the table can be
 * attributed to somebody instead of silently having a hole in it.
 */
public final class AuditActor {

    public static final String SYSTEM = "system";

    private AuditActor() {}

    public static String current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? SYSTEM : name;
    }
}

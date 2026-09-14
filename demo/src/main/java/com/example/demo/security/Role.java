package com.example.demo.security;

/**
 * Access levels recognised by the console.
 *
 * <p>They are strictly ordered in capability: a {@code VIEWER} may read, an
 * {@code ANALYST} may additionally act on findings (transition an incident,
 * comment on it, trigger a scan or a simulation), and an {@code ADMIN} may do
 * everything an analyst can. The name is carried verbatim in the {@code role}
 * claim of an access token and is mapped to the {@code ROLE_}-prefixed
 * authority the method security expressions are written against.
 */
public enum Role {
    ADMIN,
    ANALYST,
    VIEWER;

    /** Authority name for this role, in the form method security expects. */
    public String authority() {
        return "ROLE_" + name();
    }
}

package com.cloud.gateway.domain.security;

import java.util.Set;

/** Scope and audience requirements are both subset (AND), never any-match. */
public final class SubjectAccessRules {
    private SubjectAccessRules() { }

    public static boolean hasRequiredScopes(Set<String> actual, Set<String> required) {
        return actual.containsAll(required);
    }

    public static boolean hasRequiredAudiences(Set<String> actual, Set<String> required) {
        return actual.containsAll(required);
    }
}

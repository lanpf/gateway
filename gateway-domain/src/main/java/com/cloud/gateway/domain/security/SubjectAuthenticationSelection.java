package com.cloud.gateway.domain.security;

import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;

/** One authentication-family choice shared by application orchestration and entry adapters. */
public enum SubjectAuthenticationSelection {
    HOST, BROWSER, IGNORE;

    public static SubjectAuthenticationSelection from(SubjectAuthenticationRequirement requirement) {
        // v1 authenticates only the host session family; endpoint requirements no longer carry session type.
        return switch (requirement) {
            case REQUIRED, ANONYMOUS -> HOST;
        };
    }
}

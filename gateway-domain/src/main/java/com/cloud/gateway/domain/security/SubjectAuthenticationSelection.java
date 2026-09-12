package com.cloud.gateway.domain.security;

import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;

/** One authentication-family choice shared by application orchestration and entry adapters. */
public enum SubjectAuthenticationSelection {
    HOST, BROWSER, IGNORE;

    public static SubjectAuthenticationSelection from(SubjectAuthencationRequirement requirement) {
        return switch (requirement) {
            case HOST_SESSION_REQUIRED, HOST_SESSION_OPTIONAL -> HOST;
            case BROWSER_SESSION_REQUIRED -> BROWSER;
            case IGNORE -> IGNORE;
        };
    }
}

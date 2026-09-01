package com.cloud.gateway.domain.policy;

public enum SubjectRequirement {
    IGNORE,
    HOST_SESSION_REQUIRED,
    HOST_SESSION_OPTIONAL,
    BROWSER_SESSION_REQUIRED
}

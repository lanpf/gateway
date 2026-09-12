package com.cloud.gateway.domain.policy.capability;

/** Protocol-independent request facts used for shared-cache admission. */
public record CacheRequestFacts(String method, boolean authorized, boolean streaming,
        boolean subjectPresent, boolean credentialsPresent, boolean bodyPresent,
        boolean rangeRequested, boolean conditional, boolean cacheDirectivesPresent,
        boolean bodyFramingPresent) { }

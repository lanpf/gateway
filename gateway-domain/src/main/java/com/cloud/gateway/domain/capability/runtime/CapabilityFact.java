package com.cloud.gateway.domain.capability.runtime;

/** Availability, not a caller-supplied assertion of trust. */
public enum CapabilityFact {
    /** A unique endpoint has been matched; this is not an access grant. */
    ENDPOINT,
    ACCESS_GRANTED,
    VERIFIED_CLIENT,
    AUTHENTICATED_SUBJECT
}

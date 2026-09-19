package com.cloud.gateway.domain.policy.execution;

/** Availability, not a caller-supplied assertion of trust. */
public enum CapabilityFact {
    REQUEST, ENDPOINT, ACCESS_GRANTED, VERIFIED_CLIENT, AUTHENTICATED_SUBJECT
}

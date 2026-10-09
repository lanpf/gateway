package com.cloud.gateway.domain.capability.runtime;

/**
 * Fixed execution points, not configurable filter ordering. Authentication and access evaluation
 * separate the ingress slots; FORWARD runs only after admission permits forwarding.
 */
public enum CapabilitySlot {
    /** Before authentication. */
    PRE_AUTH,
    /** After authentication and access evaluation. */
    POST_AUTH,
    /** Downstream governance, before the request is forwarded. */
    FORWARD
}

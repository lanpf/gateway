package com.cloud.gateway.application.governance;

import com.cloud.gateway.domain.policy.capability.CircuitBreakerPolicy;

/** Admission and completion of a real downstream attempt, never the authentication pipeline. */
public interface DownstreamCircuitBreaker {
    Permit acquire(String routeId, CircuitBreakerPolicy policy);

    enum Outcome { SUCCESS, UPSTREAM_FAILURE, IGNORED }

    interface Permit {
        boolean allowed();

        /** Exactly-once reporting is enforced by the adapter; IGNORED also releases half-open admission. */
        void complete(Outcome outcome);
    }
}

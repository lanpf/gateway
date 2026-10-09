package com.cloud.gateway.domain.capability.runtime;

/** Typed capability result. Its HTTP projection belongs to interfaces. */
public interface CapabilityOutcome {
    Disposition disposition();

    /**
     * Controls the ordinary capability chain only. The concrete outcome carries the business
     * meaning and any response payload; stopping is not necessarily a rejection and must not skip
     * mandatory resource cleanup.
     */
    enum Disposition {
        /** No opinion; the remaining units run. */
        CONTINUE,
        /** Stop the ordinary chain and hand response handling to the concrete outcome's adapter. */
        STOP
    }
}

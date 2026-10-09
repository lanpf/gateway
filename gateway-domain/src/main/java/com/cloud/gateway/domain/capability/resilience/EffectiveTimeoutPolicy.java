package com.cloud.gateway.domain.capability.resilience;

/** Non-null resolved policy; a null config explicitly means no capability-level timeout. */
public record EffectiveTimeoutPolicy(TimeoutConfig config) {

    public static EffectiveTimeoutPolicy unconfigured() {
        return new EffectiveTimeoutPolicy(null);
    }

    public boolean configured() {
        return config != null;
    }
}

package com.cloud.gateway.domain.capability;

import java.util.List;

/**
 * Pure, immutable rules over all configurations of one capability, including unbound sources.
 * Implementations must be deterministic and safe for concurrent validation under fixed rules.
 */
public interface CapabilityConfigRule<C extends CapabilityConfig> {
    CapabilityId id();

    Class<C> configType();

    /** Non-null immutable list, without null elements or priority ordering; absence supplies an empty list. */
    void validate(List<C> configs);
}

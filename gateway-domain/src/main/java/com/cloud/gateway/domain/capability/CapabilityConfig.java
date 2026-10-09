package com.cloud.gateway.domain.capability;

import com.cloud.framework.core.error.FrameworkException;
import com.cloud.framework.core.validation.Require;

/** Immutable configuration for one registered capability. */
public interface CapabilityConfig {
    /** Identifies the owning capability, not the identity of this configuration instance. */
    CapabilityId id();

    /** Checks both the owning capability and the declared configuration type before narrowing. */
    static <C extends CapabilityConfig> C requireType(
            CapabilityId id,
            Class<C> configType,
            CapabilityConfig config) {
        Require.allNotNull(id, configType, config);

        if (!id.equals(config.id()) || !configType.isInstance(config)) {
            throw FrameworkException.invalidObject();
        }
        return configType.cast(config);
    }
}

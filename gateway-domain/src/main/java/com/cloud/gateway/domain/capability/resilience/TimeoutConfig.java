package com.cloud.gateway.domain.capability.resilience;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.framework.core.validation.Require;

import java.time.Duration;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.capability.CapabilityConfig;
import com.cloud.gateway.domain.capability.CapabilityId;

public record TimeoutConfig(Duration connect, Duration read, Duration total) implements CapabilityConfig {
    public TimeoutConfig {
        Require.allNotNull(connect, read, total);
        validate(connect);
        validate(read);
        validate(total);
    }

    private static void validate(Duration value) {
        if (value.compareTo(Duration.ofMillis(1)) < 0
                || value.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID);
        }
    }
    @Override
    public CapabilityId id() { return BuiltInCapabilityCatalog.TIMEOUT; }
}

package com.cloud.gateway.domain.capability.resilience;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.validation.Require;

import java.time.Duration;

public record TimeoutPolicy(Duration connect, Duration read, Duration total) implements com.cloud.gateway.domain.capability.CapabilityConfig {
    public TimeoutPolicy {
        validate(connect);
        validate(read);
        validate(total);
    }

    private static void validate(Duration value) {
        Require.notNull(value, DomainException::missingField);
        if (value.compareTo(Duration.ofMillis(1)) < 0
                || value.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw DomainException.invalidObjectField();
        }
    }
    @Override
    public com.cloud.gateway.domain.capability.CapabilityId capabilityId() { return TimeoutConfigDefinition.ID; }
}

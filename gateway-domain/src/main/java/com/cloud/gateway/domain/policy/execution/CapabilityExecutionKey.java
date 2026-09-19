package com.cloud.gateway.domain.policy.execution;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

/** Null slot identifies request-wide execution; otherwise identity is scoped to one slot. */
public record CapabilityExecutionKey(String capability, String unitId, CapabilitySlot slot) {
    public CapabilityExecutionKey {
        Require.notBlank(capability, DomainException::missingField);
        Require.notBlank(unitId, DomainException::missingField);
    }
}

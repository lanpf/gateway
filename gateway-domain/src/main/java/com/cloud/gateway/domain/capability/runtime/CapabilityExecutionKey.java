package com.cloud.gateway.domain.capability.runtime;


import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.framework.core.validation.Require;

/** Null slot identifies request-wide execution; otherwise identity is scoped to one slot. */
public record CapabilityExecutionKey(CapabilityId capability, String unitId, CapabilitySlot slot) {
    public CapabilityExecutionKey {
        Require.notNull(capability);
        Require.notBlank(unitId);
    }
}

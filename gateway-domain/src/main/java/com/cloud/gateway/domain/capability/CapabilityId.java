package com.cloud.gateway.domain.capability;

import com.cloud.gateway.domain.common.AbstractStringEntityId;

/** Stable code-owned capability identifier, shared by configuration and execution. */
public final class CapabilityId extends AbstractStringEntityId {
    public CapabilityId(String value) { super(value); }
}

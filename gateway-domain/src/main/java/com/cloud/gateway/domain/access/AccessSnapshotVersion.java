package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public record AccessSnapshotVersion(long value) {
    public AccessSnapshotVersion {
        Require.positive(value, DomainException::invalidField);
    }
}

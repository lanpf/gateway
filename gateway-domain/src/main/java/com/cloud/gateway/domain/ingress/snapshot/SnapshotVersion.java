package com.cloud.gateway.domain.ingress.snapshot;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public record SnapshotVersion(long value) {
    public SnapshotVersion {
        Require.positive(value, DomainException::invalidField);
    }
}

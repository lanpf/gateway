package com.cloud.gateway.domain.ingress.snapshot;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

public record SnapshotVersion(long value) {
    public SnapshotVersion {
        Require.positive(value, DomainException::invalidObjectField);
    }
}

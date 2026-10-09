package com.cloud.gateway.domain.ingress.snapshot;


import com.cloud.framework.core.validation.Require;

public record SnapshotVersion(long value) {
    public SnapshotVersion {
        Require.positive(value);
    }
}

package com.cloud.gateway.application.policy;

import java.util.Objects;

public record VersionedPolicyDefinition<T>(T value, String sourceRevision) {
    public VersionedPolicyDefinition {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(sourceRevision, "sourceRevision must not be null");
    }
}

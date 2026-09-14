package com.cloud.gateway.application.access.definition;

import java.util.Objects;

public record VersionedAccessDefinition<T>(T value, String sourceRevision) {
    public VersionedAccessDefinition {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(sourceRevision, "sourceRevision must not be null");
    }
}

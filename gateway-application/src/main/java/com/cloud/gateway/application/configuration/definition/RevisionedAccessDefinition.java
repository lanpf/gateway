package com.cloud.gateway.application.configuration.definition;

import com.cloud.gateway.domain.ingress.snapshot.Revision;

import java.util.Objects;

/**
 * One definition value together with the file-level revision it was read at. The revision is the
 * CAS token of the whole collection document — not a version of the value itself — which is why the
 * pair is named for the revision rather than for a version.
 */
public record RevisionedAccessDefinition<T>(T value, Revision sourceRevision) {
    public RevisionedAccessDefinition {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(sourceRevision, "sourceRevision must not be null");
    }
}

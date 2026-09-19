package com.cloud.gateway.application.configuration.definition;

import com.cloud.gateway.domain.ingress.snapshot.Revision;

public interface WritableAccessDefinitionRepository<T> extends AccessDefinitionRepository<T> {
    /** Stores the definition when the value matches; returns its new value token, null on conflict. */
    Revision compareAndSet(Revision expectedSourceRevision, T definition);
}

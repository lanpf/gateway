package com.cloud.gateway.application.access.definition;

public interface WritableAccessDefinitionRepository<T> extends AccessDefinitionRepository<T> {
    /** Stores the definition when the revision matches; returns its new revision token, null on conflict. */
    String compareAndSet(String expectedSourceRevision, T definition);
}

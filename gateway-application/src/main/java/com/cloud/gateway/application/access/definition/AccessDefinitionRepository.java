package com.cloud.gateway.application.access.definition;

public interface AccessDefinitionRepository<T> {
    VersionedAccessDefinition<T> load();
}

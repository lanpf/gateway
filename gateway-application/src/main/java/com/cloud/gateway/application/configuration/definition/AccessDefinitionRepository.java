package com.cloud.gateway.application.configuration.definition;

public interface AccessDefinitionRepository<T> {
    RevisionedAccessDefinition<T> load();
}

package com.cloud.gateway.application.policy;

public interface PolicyDefinitionRepository<T> {
    VersionedPolicyDefinition<T> load();
}

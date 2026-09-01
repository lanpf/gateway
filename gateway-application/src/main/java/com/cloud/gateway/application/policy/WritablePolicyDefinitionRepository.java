package com.cloud.gateway.application.policy;

public interface WritablePolicyDefinitionRepository<T> extends PolicyDefinitionRepository<T> {
    boolean compareAndSet(String expectedSourceRevision, T definition);
}

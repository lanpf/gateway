package com.cloud.gateway.application.policy;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AbstractPolicyCommandService<T> {
    private final PolicyDefinitionRepository<T> repository;
    private final PolicyRollbackState rollbackState;
    private final PolicyPublishLock publishLock;


}

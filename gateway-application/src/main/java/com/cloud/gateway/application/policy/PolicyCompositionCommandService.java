package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.header.HeaderRules;


/** Replaces the complete bundles/bindings Definition; publication remains a separate use case. */
public final class PolicyCompositionCommandService {
    private final WritablePolicyCompositionDefinitionRepository repository;
    private final PolicyDefinitionWriter writer;
    private final HeaderRules headerRules;

    public PolicyCompositionCommandService(WritablePolicyCompositionDefinitionRepository repository,
            PolicyRollbackState rollbackState, PolicyPublishLock publishLock, HeaderRules headerRules) {
        this.repository = repository;
        this.writer = new PolicyDefinitionWriter(rollbackState, publishLock);
        this.headerRules = headerRules;
    }

    public void save(SavePolicyCompositionCommand command) {
        headerRules.validate(command.policies());
        writer.update(repository, command.expectedSourceRevision(), current -> command.policies());
    }
}

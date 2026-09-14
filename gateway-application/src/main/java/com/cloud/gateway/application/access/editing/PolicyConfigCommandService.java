package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.application.access.publication.AccessPublishLock;
import com.cloud.gateway.application.access.publication.AccessRollbackState;
import com.cloud.gateway.domain.policy.header.HeaderRules;


/** Replaces the complete bundles/bindings Definition; publication remains a separate use case. */
public final class PolicyConfigCommandService {
    private final com.cloud.gateway.application.access.definition.WritablePolicyConfigDefinitionRepository repository;
    private final com.cloud.gateway.application.access.editing.AccessDefinitionWriter writer;
    private final HeaderRules headerRules;

    public PolicyConfigCommandService(com.cloud.gateway.application.access.definition.WritablePolicyConfigDefinitionRepository repository,
            AccessRollbackState rollbackState, AccessPublishLock publishLock, HeaderRules headerRules) {
        this.repository = repository;
        this.writer = new com.cloud.gateway.application.access.editing.AccessDefinitionWriter(rollbackState, publishLock);
        this.headerRules = headerRules;
    }

    public void save(SavePolicyConfigCommand command) {
        headerRules.validate(command.policies());
        writer.update(repository, command.expectedSourceRevision(), current -> command.policies());
    }
}

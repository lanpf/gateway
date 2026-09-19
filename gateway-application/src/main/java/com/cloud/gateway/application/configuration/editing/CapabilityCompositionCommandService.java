package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.application.configuration.definition.WritableCapabilityCompositionDefinitionRepository;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackState;
import com.cloud.gateway.domain.policy.header.HeaderRules;


/** Replaces the complete bundles/bindings Definition; publication remains a separate use case. */
public final class CapabilityCompositionCommandService {
    private final WritableCapabilityCompositionDefinitionRepository repository;
    private final AccessDefinitionWriter writer;
    private final HeaderRules headerRules;

    public CapabilityCompositionCommandService(WritableCapabilityCompositionDefinitionRepository repository,
            AccessRollbackState rollbackState, AccessPublishLock publishLock, HeaderRules headerRules) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
        this.headerRules = headerRules;
    }

    /** Returns the new source value so consecutive edits do not need to re-read the definition. */
    public Revision save(SaveCapabilityCompositionCommand command) {
        headerRules.validate(command.policyConfig());
        return writer.update(repository, command.expectedSourceRevision(), current -> command.policyConfig());
    }
}

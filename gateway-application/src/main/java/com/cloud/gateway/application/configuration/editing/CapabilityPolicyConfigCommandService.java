package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.application.configuration.definition.WritableCapabilityPolicyConfigDefinitionRepository;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackState;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator;


/** Replaces the complete bundles/bindings Definition; publication remains a separate use case. */
public final class CapabilityPolicyConfigCommandService {
    private final WritableCapabilityPolicyConfigDefinitionRepository repository;
    private final AccessDefinitionWriter writer;
    private final CapabilityPolicyConfigValidator validator;

    public CapabilityPolicyConfigCommandService(WritableCapabilityPolicyConfigDefinitionRepository repository,
            AccessRollbackState rollbackState, AccessPublishLock publishLock, CapabilityPolicyConfigValidator validator) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
        this.validator = validator;
    }

    /** Returns the new source value so consecutive edits do not need to re-read the definition. */
    public Revision save(SaveCapabilityPolicyConfigCommand command) {
        validator.validate(command.capabilityPolicyConfig());
        return writer.update(repository, command.expectedSourceRevision(), current -> command.capabilityPolicyConfig());
    }
}

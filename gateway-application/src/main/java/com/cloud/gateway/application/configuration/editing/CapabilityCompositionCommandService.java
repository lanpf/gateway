package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.application.configuration.definition.WritableCapabilityCompositionDefinitionRepository;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackState;
import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;


/** Replaces the complete bundles/bindings Definition; publication remains a separate use case. */
public final class CapabilityCompositionCommandService {
    private final WritableCapabilityCompositionDefinitionRepository repository;
    private final AccessDefinitionWriter writer;
    private final HeaderAdmissionRules headerRules;
    private final com.cloud.gateway.domain.capability.configuration.CapabilityConfigRegistry capabilities;

    public CapabilityCompositionCommandService(WritableCapabilityCompositionDefinitionRepository repository,
            AccessRollbackState rollbackState, AccessPublishLock publishLock, HeaderAdmissionRules headerRules, com.cloud.gateway.domain.capability.configuration.CapabilityConfigRegistry capabilities) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
        this.headerRules = headerRules;
        this.capabilities = capabilities;
    }

    /** Returns the new source value so consecutive edits do not need to re-read the definition. */
    public Revision save(SaveCapabilityCompositionCommand command) {
        capabilities.validate(command.capabilityComposition().bundles().values());
        command.capabilityComposition().bundles().values()
                .forEach(bundle -> headerRules.validate(bundle.find(com.cloud.gateway.domain.capability.header.HeaderConfigDefinition.INSTANCE).orElse(null)));
        return writer.update(repository, command.expectedSourceRevision(), current -> command.capabilityComposition());
    }
}

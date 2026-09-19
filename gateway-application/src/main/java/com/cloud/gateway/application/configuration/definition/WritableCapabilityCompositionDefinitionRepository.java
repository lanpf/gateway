package com.cloud.gateway.application.configuration.definition;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
public interface WritableCapabilityCompositionDefinitionRepository extends CapabilityCompositionDefinitionRepository,
        WritableAccessDefinitionRepository<CapabilityComposition> { }

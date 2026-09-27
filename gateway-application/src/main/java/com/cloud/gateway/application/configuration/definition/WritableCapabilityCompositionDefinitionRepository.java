package com.cloud.gateway.application.configuration.definition;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
public interface WritableCapabilityCompositionDefinitionRepository extends CapabilityCompositionDefinitionRepository,
        WritableAccessDefinitionRepository<CapabilityComposition> { }

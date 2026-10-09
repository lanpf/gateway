package com.cloud.gateway.application.configuration.definition;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
public interface WritableCapabilityPolicyConfigDefinitionRepository extends CapabilityPolicyConfigDefinitionRepository,
        WritableAccessDefinitionRepository<CapabilityPolicyConfig> { }

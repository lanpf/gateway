package com.cloud.gateway.application.policy;
import com.cloud.gateway.domain.policy.bundle.PolicyComposition;
public interface WritablePolicyCompositionDefinitionRepository extends PolicyCompositionDefinitionRepository,
        WritablePolicyDefinitionRepository<PolicyComposition> { }

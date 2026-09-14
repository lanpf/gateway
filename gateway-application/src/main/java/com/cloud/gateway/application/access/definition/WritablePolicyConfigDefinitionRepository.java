package com.cloud.gateway.application.access.definition;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
public interface WritablePolicyConfigDefinitionRepository extends PolicyConfigDefinitionRepository,
        WritableAccessDefinitionRepository<PolicyConfig> { }

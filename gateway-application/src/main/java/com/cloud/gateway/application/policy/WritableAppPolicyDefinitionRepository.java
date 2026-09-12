package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.app.AppId;

import java.util.Map;

public interface WritableAppPolicyDefinitionRepository
        extends AppPolicyDefinitionRepository,
        WritablePolicyDefinitionRepository<Map<AppId, AppPolicy>> {
}

package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.app.AppId;

import java.util.Map;

public interface AppPolicyDefinitionRepository
        extends PolicyDefinitionRepository<Map<AppId, AppPolicy>> {
}

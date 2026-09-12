package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.app.AppId;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AppPolicyCommandService {
    private final WritableAppPolicyDefinitionRepository repository;
    private final PolicyDefinitionWriter writer;

    public AppPolicyCommandService(WritableAppPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState, PolicyPublishLock publishLock) {
        this.repository = repository;
        this.writer = new PolicyDefinitionWriter(rollbackState, publishLock);
    }

    public void save(SaveAppPolicyCommand command) {
        writer.update(repository, command.expectedSourceRevision(), current -> {
            AppId appId = command.policy().appId();
            AppPolicy existing = current.get(appId);
            if (existing != null && !existing.appId().equals(appId)) {
                // This should never happen, but we should log it if it does.
                throw new IllegalStateException(
                        "AppId mismatch: existing=" + existing.appId() + ", new=" + appId);
            }
            Map<AppId, AppPolicy> updated = new LinkedHashMap<>(current);
            updated.put(appId, command.policy());
            return Map.copyOf(updated);
        });
    }
}

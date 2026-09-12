package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientAccessPolicyCommandService {
    private final WritableClientPolicyDefinitionRepository repository;
    private final PolicyDefinitionWriter writer;

    public ClientAccessPolicyCommandService(WritableClientPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState, PolicyPublishLock publishLock) {
        this.repository = repository;
        this.writer = new PolicyDefinitionWriter(rollbackState, publishLock);
    }

    public void execute(SaveClientAccessPolicyCommand command) {
        writer.update(repository, command.expectedSourceRevision(), current -> {
            Map<ClientId, ClientPolicy> updated = new LinkedHashMap<>(current);
            ClientPolicy requested = command.policy();
            ClientPolicy existing = current.get(requested.clientId());
            ClientPolicy next = existing == null
                    ? new ClientPolicy(requested.clientId(), requested.allowedApps(), requested.status())
                    : existing.withAllowedApps(requested.allowedApps(), requested.status());
            updated.put(next.clientId(), next);
            return Map.copyOf(updated);
        });
    }
}

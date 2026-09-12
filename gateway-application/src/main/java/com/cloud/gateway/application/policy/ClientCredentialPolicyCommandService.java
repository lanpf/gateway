package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientCredentialPolicyCommandService {
    private final WritableClientPolicyDefinitionRepository repository;
    private final PolicyDefinitionWriter writer;

    public ClientCredentialPolicyCommandService(WritableClientPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState, PolicyPublishLock publishLock) {
        this.repository = repository;
        this.writer = new PolicyDefinitionWriter(rollbackState, publishLock);
    }

    public void save(SaveClientCredentialPolicyCommand command) {
        writer.update(repository, command.expectedSourceRevision(), current -> {
            ClientPolicy existing = Require.notNull(current.get(command.clientId()), DomainException::invalidField);
            Map<ClientId, ClientPolicy> updated = new LinkedHashMap<>(current);
            updated.put(existing.clientId(), existing.withCredentials(command.credentials()));
            return Map.copyOf(updated);
        });
    }
}

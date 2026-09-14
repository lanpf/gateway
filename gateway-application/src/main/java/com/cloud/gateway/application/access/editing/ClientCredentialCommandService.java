package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.validation.Require;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ClientCredentialCommandService {
    private final com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository repository;
    private final AccessDefinitionWriter writer;

    public ClientCredentialCommandService(com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository repository,
            com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState, com.cloud.gateway.application.access.publication.AccessPublishLock publishLock) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
    }

    /** Whole-pair rotation that requires the client to exist and preserves its access settings and status. */
    public String save(SaveClientCredentialCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            Client existing = Require.that(current.find(command.clientId()).orElse(null), Objects::nonNull,
                    () -> new DomainException(DomainError.CLIENT_NOT_FOUND));
            return withClient(current, new Client(existing.clientId(), existing.status(),
                    new ClientAccess(existing.access().allowedApps(), command.credentials())));
        });
    }

    private static ClientRegistry withClient(ClientRegistry current, Client next) {
        Map<ClientId, Client> updated = new LinkedHashMap<>(current.asMap());
        updated.put(next.clientId(), next);
        return new ClientRegistry(updated.values());
    }
}

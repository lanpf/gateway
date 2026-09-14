package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ClientAccessCommandService {
    private final com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository repository;
    private final AccessDefinitionWriter writer;

    public ClientAccessCommandService(com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository repository,
            com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState, com.cloud.gateway.application.access.publication.AccessPublishLock publishLock) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
    }

    /** Content-only upsert: preserves credentials, creation initializes status to ENABLED, updates keep it. */
    public String save(SaveClientAccessCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            Client existing = current.find(command.clientId()).orElse(null);
            Client next = existing == null
                    ? new Client(command.clientId(), ClientStatus.ENABLED,
                            new ClientAccess(command.allowedAppIds(), null))
                    : new Client(command.clientId(), existing.status(),
                            new ClientAccess(command.allowedAppIds(), existing.access().credentials()));
            return withClient(current, next);
        });
    }

    /** Dedicated enable/disable transition; the only command that changes a client's status. */
    public String setClientStatus(SetClientStatusCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            Client existing = Require.that(current.find(command.clientId()).orElse(null), Objects::nonNull,
                    () -> new DomainException(DomainError.CLIENT_NOT_FOUND));
            return withClient(current, new Client(existing.clientId(), command.status(), existing.access()));
        });
    }

    private static ClientRegistry withClient(ClientRegistry current, Client next) {
        Map<ClientId, Client> updated = new LinkedHashMap<>(current.asMap());
        updated.put(next.clientId(), next);
        return new ClientRegistry(updated.values());
    }
}

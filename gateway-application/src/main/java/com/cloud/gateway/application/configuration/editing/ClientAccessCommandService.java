package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.application.configuration.definition.WritableClientAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackState;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientAccessCommandService {
    private final WritableClientAccessDefinitionRepository repository;
    private final AccessDefinitionWriter writer;

    public ClientAccessCommandService(WritableClientAccessDefinitionRepository repository,
                                      AccessRollbackState rollbackState,
                                      AccessPublishLock publishLock) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
    }

    /** Content-only upsert: preserves credentials, creation initializes status to ENABLED, updates keep it. */
    public Revision save(SaveClientAccessCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            Client existing = current.find(command.clientId()).orElse(null);
            ClientAccess access = existing == null
                    ? new ClientAccess(command.allowedAppIds(), null)
                    : new ClientAccess(command.allowedAppIds(), existing.access().credentials());
            Client next = existing == null
                    ? new Client(command.clientId(), ClientStatus.ENABLED, access)
                    : new Client(command.clientId(), existing.status(), access);
            return withClient(current, next);
        });
    }

    /** Dedicated enable/disable transition; the only command that changes a client's status. */
    public Revision setClientStatus(SetClientStatusCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            Client existing = current.find(command.clientId()).orElseThrow(() -> new DomainException(DomainError.CLIENT_NOT_FOUND));
            Client next =  new Client(existing.clientId(), command.status(), existing.access());
            return withClient(current, next);
        });
    }

    private static ClientRegistry withClient(ClientRegistry current, Client next) {
        Map<ClientId, Client> updated = new LinkedHashMap<>(current.asMap());
        updated.put(next.clientId(), next);
        return new ClientRegistry(updated.values());
    }
}

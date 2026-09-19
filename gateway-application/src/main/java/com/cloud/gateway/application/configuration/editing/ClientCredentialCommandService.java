package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.application.configuration.definition.WritableClientAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackState;
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
    private final WritableClientAccessDefinitionRepository repository;
    private final AccessDefinitionWriter writer;

    public ClientCredentialCommandService(WritableClientAccessDefinitionRepository repository,
                                          AccessRollbackState rollbackState,
                                          AccessPublishLock publishLock) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
    }

    /** Whole-pair rotation that requires the client to exist and preserves its access settings and status. */
    public Revision save(SaveClientCredentialCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            Client existing = current.find(command.clientId()).orElseThrow(() -> new DomainException(DomainError.CLIENT_NOT_FOUND));
            ClientAccess access = new ClientAccess(existing.access().allowedApps(), command.credentials());
            Client next = new Client(existing.clientId(), existing.status(), access);
            return withClient(current, next);
        });
    }

    private static ClientRegistry withClient(ClientRegistry current, Client next) {
        Map<ClientId, Client> updated = new LinkedHashMap<>(current.asMap());
        updated.put(next.clientId(), next);
        return new ClientRegistry(updated.values());
    }
}

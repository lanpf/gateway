package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.application.configuration.definition.WritableEndpointAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackState;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EndpointAccessCommandService {
    private final WritableEndpointAccessDefinitionRepository repository;
    private final AccessDefinitionWriter writer;

    public EndpointAccessCommandService(WritableEndpointAccessDefinitionRepository repository,
                                        AccessRollbackState rollbackState,
                                        AccessPublishLock publishLock) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
    }

    /** Content-only upsert: creation initializes status to ENABLED, updates preserve the existing status. */
    public Revision save(SaveEndpointAccessCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            EndpointAccess access = new EndpointAccess(command.clientVerificationRequirement(),
                    command.subjectAuthenticationRequirement(), command.requiredScopes(), command.requiredAudiences());
            Endpoint next = current.find(command.endpointId())
                    .map(existing -> existing.edit(command.serviceId(), command.routeId(), command.matchers(), command.tags(), access))
                    .orElseGet(() -> Endpoint.create(command.endpointId(), command.serviceId(), command.routeId(),
                            command.matchers(), command.tags(), access));
            return withEndpoint(current, next);
        });
    }

    /** Dedicated enable/disable transition; the only command that changes an endpoint's status. */
    public Revision setEndpointStatus(SetEndpointStatusCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            Endpoint existing = current.find(command.endpointId()).orElseThrow(() -> new DomainException(DomainError.ENDPOINT_NOT_FOUND));
            Endpoint next = existing.changeStatus(command.status());
            return withEndpoint(current, next);
        });
    }

    private static EndpointRegistry withEndpoint(EndpointRegistry current, Endpoint next) {
        Map<EndpointId, Endpoint> updated = new LinkedHashMap<>(current.asMap());
        updated.put(next.getId(), next);
        return new EndpointRegistry(updated.values());
    }
}

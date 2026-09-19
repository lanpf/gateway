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
            EndpointStatus status = current.find(command.endpointId())
                    .map(Endpoint::status)
                    .orElse(EndpointStatus.ENABLED);

            EndpointAccess access = new EndpointAccess(command.clientVerificationRequirement(),
                    command.subjectAuthenticationRequirement(), command.requiredScopes(), command.requiredAudiences());
            Endpoint next = new Endpoint(command.endpointId(), command.serviceId(), command.routeId(),
                            command.matchers(), command.tags(), status, access);
            return withEndpoint(current, next);
        });
    }

    /** Dedicated enable/disable transition; the only command that changes an endpoint's status. */
    public Revision setEndpointStatus(SetEndpointStatusCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            Endpoint existing = current.find(command.endpointId()).orElseThrow(() -> new DomainException(DomainError.ENDPOINT_NOT_FOUND));
            Endpoint next = new Endpoint(existing.endpointId(), existing.serviceId(), existing.routeId(),
                    existing.matchers(), existing.tags(), command.status(), existing.access());
            return withEndpoint(current, next);
        });
    }

    private static EndpointRegistry withEndpoint(EndpointRegistry current, Endpoint next) {
        Map<EndpointId, Endpoint> updated = new LinkedHashMap<>(current.asMap());
        updated.put(next.endpointId(), next);
        return new EndpointRegistry(updated.values());
    }
}

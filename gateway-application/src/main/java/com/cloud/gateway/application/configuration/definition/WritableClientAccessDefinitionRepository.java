package com.cloud.gateway.application.configuration.definition;

import com.cloud.gateway.domain.client.ClientRegistry;

public interface WritableClientAccessDefinitionRepository
        extends ClientAccessDefinitionRepository,
        WritableAccessDefinitionRepository<ClientRegistry> {
}

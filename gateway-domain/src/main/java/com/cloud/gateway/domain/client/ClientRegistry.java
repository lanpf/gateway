package com.cloud.gateway.domain.client;

import com.cloud.gateway.domain.common.AbstractAggregateRootRegistry;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Collection;

public final class ClientRegistry extends AbstractAggregateRootRegistry<Client, ClientId> {

    public ClientRegistry(Collection<Client> clients) {
        super(clients);
    }

    @Override
    protected DomainException ambiguous() {
        return new DomainException(DomainError.CLIENT_AMBIGUOUS);
    }

}

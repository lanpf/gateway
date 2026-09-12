package com.cloud.gateway.domain.client;

import com.cloud.gateway.domain.common.AggregateRootRegistry;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Collection;

public final class ClientRegistry extends AggregateRootRegistry<Client, ClientId> {

    public ClientRegistry(Collection<Client> clients) {
        super(clients);
    }

    @Override
    protected DomainException ambiguous() {
        return new DomainException(DomainError.CLIENT_AMBIGUOUS);
    }

}

package com.cloud.gateway.domain.client;

import com.cloud.gateway.domain.common.AggregateRootRegistry;

import java.util.Collection;

public final class ClientRegistry extends AggregateRootRegistry<Client, ClientId> {

    public ClientRegistry(Collection<Client> clients) {
        super(clients);
    }

}

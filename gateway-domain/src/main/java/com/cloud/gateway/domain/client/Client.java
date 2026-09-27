package com.cloud.gateway.domain.client;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

import com.cloud.framework.domain.AggregateRoot;
import com.cloud.gateway.domain.app.AppId;

public record Client(
        ClientId clientId,
        ClientStatus status,
        ClientAccess access
) implements AggregateRoot<ClientId> {
    public Client {
        Require.notNull(clientId, DomainException::missingField);
        Require.notNull(status, DomainException::missingField);
        Require.notNull(access, DomainException::missingField);
    }

    @Override
    public ClientId getId() {
        return clientId();
    }

    public boolean enabled() {
        return status == ClientStatus.ENABLED;
    }

    public boolean allowsApp(AppId appId) {
        return enabled() && access.allowsApp(appId);
    }
}

package com.cloud.gateway.domain.client;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.AggregateRoot;
import lombok.Getter;
import com.cloud.gateway.domain.app.AppId;
import java.util.Set;

/** Immutable aggregate; transitions preserve identity. */
@Getter
public final class Client implements AggregateRoot<ClientId> {
    private final ClientId id;
    private final ClientStatus status;
    private final ClientAccess access;

    public Client(ClientId id, ClientStatus status, ClientAccess access) {
        Require.allNotNull(id, status, access);
        this.id = id;
        this.status = status;
        this.access = access;
    }


    public static Client create(ClientId id, Set<AppId> allowedApps) {
        return new Client(id, ClientStatus.ENABLED, new ClientAccess(allowedApps, null));
    }

    public Client edit(Set<AppId> allowedApps) {
        return new Client(id, status, new ClientAccess(allowedApps, access.credentials()));
    }

    public Client changeStatus(ClientStatus status) {
        return new Client(id, status, access);
    }

    public Client rotateCredentials(Credentials credentials) {
        Require.notNull(credentials);
        return new Client(id, status, new ClientAccess(access.allowedApps(), credentials));
    }

    public boolean enabled() { return status == ClientStatus.ENABLED; }

    public boolean allowsApp(AppId appId) { return enabled() && access.allowsApp(appId); }

    public boolean sameDefinition(Client other) {
        return other != null && id.equals(other.id) && status == other.status
                && access.equals(other.access);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Client that && id.equals(that.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}

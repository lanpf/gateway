package com.cloud.gateway.domain.client;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Set;

public record ClientAccess(
        Set<AppId> allowedApps,
        Credentials credentials
) {
    public ClientAccess {
        allowedApps = allowedApps == null ? Set.of() : allowedApps;
        allowedApps.forEach(appId -> Require.notNull(appId, DomainException::invalidField));
        allowedApps = Set.copyOf(allowedApps);
    }

    boolean allowsApp(AppId appId) {
        return allowedApps.contains(appId);
    }
}

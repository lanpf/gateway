package com.cloud.gateway.domain.client;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

import com.cloud.gateway.domain.app.AppId;

import java.util.Set;

public record ClientAccess(
        Set<AppId> allowedApps,
        Credentials credentials
) {
    public ClientAccess {
        allowedApps = allowedApps == null ? Set.of() : allowedApps;
        allowedApps.forEach(value -> Require.notNull(value, DomainException::missingField));
        allowedApps = Set.copyOf(allowedApps);
    }

    boolean allowsApp(AppId appId) {
        return allowedApps.contains(appId);
    }
}

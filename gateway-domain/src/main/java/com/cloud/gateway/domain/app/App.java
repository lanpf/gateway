package com.cloud.gateway.domain.app;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

import com.cloud.framework.domain.AggregateRoot;

public record App(
        AppId appId,
        String platform,
        AppStatus status,
        AppAccess access
) implements AggregateRoot<AppId>
{
    public App {
        Require.notNull(appId, DomainException::missingField);
        platform = Require.notBlank(platform, DomainException::invalidObjectField).trim();
        Require.notNull(status, DomainException::missingField);
        Require.notNull(access, DomainException::missingField);
    }

    @Override
    public AppId getId() {
        return appId();
    }

    public boolean enabled() { return status == AppStatus.ENABLED; }

    public boolean allowsOrigin(String origin) {
        if (!enabled()) {
            return false;
        }
        return access.allowsOrigin(origin);
    }
}

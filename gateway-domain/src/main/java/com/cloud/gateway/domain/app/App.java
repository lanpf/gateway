package com.cloud.gateway.domain.app;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.AggregateRoot;
import lombok.Getter;

/** Immutable aggregate; transitions preserve identity. */
@Getter
public final class App implements AggregateRoot<AppId> {
    private final AppId id;
    private final String platform;
    private final AppStatus status;
    private final AppAccess access;

    public App(AppId id, String platform, AppStatus status, AppAccess access) {
        Require.allNotNull(id, status, access);
        this.id = id;
        this.platform = Require.notBlank(platform).trim();
        this.status = status;
        this.access = access;
    }


    public static App create(AppId id, String platform, AppAccess access) {
        return new App(id, platform, AppStatus.ENABLED, access);
    }

    public App edit(String platform, AppAccess access) {
        return new App(id, platform, status, access);
    }

    public App changeStatus(AppStatus status) {
        return new App(id, platform, status, access);
    }

    public boolean enabled() { return status == AppStatus.ENABLED; }

    public boolean allowsOrigin(String origin) { return enabled() && access.allowsOrigin(origin); }

    public boolean sameDefinition(App other) {
        return other != null && id.equals(other.id) && platform.equals(other.platform)
                && status == other.status && access.equals(other.access);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof App that && id.equals(that.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}

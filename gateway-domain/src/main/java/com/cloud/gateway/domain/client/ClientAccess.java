package com.cloud.gateway.domain.client;


import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.app.AppId;

import java.util.Set;

public record ClientAccess(
        Set<AppId> allowedApps,
        Credentials credentials
) {
    public ClientAccess {
        allowedApps = NullSafe.copyOf(allowedApps);
    }

    boolean allowsApp(AppId appId) {
        return allowedApps.contains(appId);
    }
}

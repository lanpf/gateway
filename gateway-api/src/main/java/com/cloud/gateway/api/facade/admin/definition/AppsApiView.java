package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.api.enums.AppStatusApiEnum;

import java.util.Set;

/**
 * Editing state of the app-policies definition. The sourceRevision is the shared CAS token of the
 * whole app collection (one definition file), not a per-app version; a null revision means the
 * definition is not initialized yet and the next save sends null/empty to create it.
 */
public record AppsApiView(String sourceRevision, Set<AppApiView> apps) {
    public AppsApiView {
        apps = apps == null ? null : NullSafe.copyOf(apps);
    }

    public record AppApiView(String appId, String platform, AppStatusApiEnum status, Set<String> allowedOrigins) {
        public AppApiView {
            allowedOrigins = allowedOrigins == null ? null : NullSafe.copyOf(allowedOrigins);
        }
    }
}

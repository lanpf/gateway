package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.gateway.api.enums.AppStatusApiEnum;

import java.util.Set;

/**
 * Editing state of the app-policies definition. The sourceRevision is the shared CAS token of the
 * whole app collection (one definition file), not a per-app version; a null revision means the
 * definition is not initialized yet and the next save sends null/empty to create it.
 */
public record AppsApiView(String sourceRevision, Set<App> apps) {

    public record App(String appId, String platform, AppStatusApiEnum status, Set<String> allowedOrigins) { }
}

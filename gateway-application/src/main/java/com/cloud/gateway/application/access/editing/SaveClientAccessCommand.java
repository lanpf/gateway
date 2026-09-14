package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import java.util.Set;

/** Content-only client access save; existing credentials are never touched by this command. */
public record SaveClientAccessCommand(String expectedSourceRevision, ClientId clientId,
                                            Set<AppId> allowedAppIds) {
    public SaveClientAccessCommand {
        allowedAppIds = allowedAppIds == null ? null : Set.copyOf(allowedAppIds);
    }
}

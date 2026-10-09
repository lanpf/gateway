package com.cloud.gateway.application.configuration.editing;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import java.util.Set;

/** Content-only client access save; existing credentials are never touched by this command. */
public record SaveClientAccessCommand(Revision expectedSourceRevision,
                                      ClientId clientId, Set<AppId> allowedAppIds) {
    public SaveClientAccessCommand {
        allowedAppIds = allowedAppIds == null ? null : NullSafe.copyOf(allowedAppIds);
    }
}

package com.cloud.gateway.application.configuration.editing;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.app.AppId;
import java.util.Set;

/** Content-only App save; status is managed by the dedicated status transition. */
public record SaveAppAccessCommand(Revision expectedSourceRevision,
                                   AppId appId, String platform, Set<String> allowedOrigins) {
    public SaveAppAccessCommand {
        allowedOrigins = allowedOrigins == null ? null : NullSafe.copyOf(allowedOrigins);
    }
}

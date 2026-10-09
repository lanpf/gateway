package com.cloud.gateway.domain.ingress.access;


import com.cloud.framework.core.validation.Require;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;

public sealed interface AccessDecision {
    record Allowed(AccessGrantedContext grantedContext, SnapshotVersion snapshotVersion) implements AccessDecision {
        public Allowed {
            Require.allNotNull(grantedContext, snapshotVersion);
        }
    }
    record Denied(AccessDenyReason denyReason, SnapshotVersion snapshotVersion) implements AccessDecision {
        public Denied {
            Require.allNotNull(denyReason, snapshotVersion);
        }
    }

    static AccessDecision allowed(AccessGrantedContext grantedContext, SnapshotVersion snapshotVersion) {
        return new AccessDecision.Allowed(grantedContext, snapshotVersion);
    }

    static AccessDecision denied(AccessDenyReason denyReason, SnapshotVersion snapshotVersion) {
        return new AccessDecision.Denied(denyReason, snapshotVersion);
    }
}

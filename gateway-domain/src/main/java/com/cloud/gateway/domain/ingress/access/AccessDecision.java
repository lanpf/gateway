package com.cloud.gateway.domain.ingress.access;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public sealed interface AccessDecision {
    record Allowed(AccessGrantedContext grantedContext, SnapshotVersion snapshotVersion) implements AccessDecision {
        public Allowed {
            Require.notNull(grantedContext, DomainException::missingField);
            Require.notNull(snapshotVersion, DomainException::missingField);
        }
    }
    record Denied(AccessDenyReason denyReason, SnapshotVersion snapshotVersion) implements AccessDecision {
        public Denied {
            Require.notNull(denyReason, DomainException::missingField);
            Require.notNull(snapshotVersion, DomainException::missingField);
        }
    }

    static AccessDecision allowed(AccessGrantedContext grantedContext, SnapshotVersion snapshotVersion) {
        return new AccessDecision.Allowed(grantedContext, snapshotVersion);
    }

    static AccessDecision denied(AccessDenyReason denyReason, SnapshotVersion snapshotVersion) {
        return new AccessDecision.Denied(denyReason, snapshotVersion);
    }
}

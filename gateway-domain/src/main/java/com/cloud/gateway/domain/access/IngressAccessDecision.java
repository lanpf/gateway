package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public sealed interface IngressAccessDecision {
    record Allowed(IngressAccessGrantedContext grantedContext, AccessSnapshotVersion snapshotVersion) implements IngressAccessDecision {
        public Allowed {
            Require.notNull(grantedContext, DomainException::missingField);
            Require.notNull(snapshotVersion, DomainException::missingField);
        }
    }
    record Denied(IngressAccessDenyReason denyReason, AccessSnapshotVersion snapshotVersion) implements IngressAccessDecision {
        public Denied {
            Require.notNull(denyReason, DomainException::missingField);
            Require.notNull(snapshotVersion, DomainException::missingField);
        }
    }

    static IngressAccessDecision allowed(IngressAccessGrantedContext grantedContext, AccessSnapshotVersion snapshotVersion) {
        return new IngressAccessDecision.Allowed(grantedContext, snapshotVersion);
    }

    static IngressAccessDecision denied(IngressAccessDenyReason denyReason, AccessSnapshotVersion snapshotVersion) {
        return new IngressAccessDecision.Denied(denyReason, snapshotVersion);
    }
}

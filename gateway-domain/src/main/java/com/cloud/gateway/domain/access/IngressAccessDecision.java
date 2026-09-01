package com.cloud.gateway.domain.access;

public sealed interface IngressAccessDecision {
    record Allowed(TrustedRequestContext trustedRequestContext) implements IngressAccessDecision { }
    record Denied(IngressAccessDenyReason reason, long policySnapshotVersion) implements IngressAccessDecision { }
}

package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.access.IngressAccessSnapshot;

public record VersionedPolicySnapshot(IngressAccessSnapshot snapshot, String sourceRevision) { }

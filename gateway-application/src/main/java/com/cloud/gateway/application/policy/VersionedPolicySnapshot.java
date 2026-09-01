package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.IngressPolicySnapshot;

public record VersionedPolicySnapshot(IngressPolicySnapshot snapshot, String sourceRevision) { }

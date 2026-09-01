package com.cloud.gateway.application.policy;

public interface PolicySnapshotProvider {
    VersionedPolicySnapshot current();
}

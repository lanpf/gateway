package com.cloud.gateway.api.policy;
public record SnapshotRollbackApiCommandOutput(long snapshotVersion, String contentHash) { }

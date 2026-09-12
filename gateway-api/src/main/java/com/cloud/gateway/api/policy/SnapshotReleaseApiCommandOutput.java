package com.cloud.gateway.api.policy;
public record SnapshotReleaseApiCommandOutput(long snapshotVersion, String contentHash) { }

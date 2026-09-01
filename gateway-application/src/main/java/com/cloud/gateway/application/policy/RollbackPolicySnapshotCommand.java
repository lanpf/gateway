package com.cloud.gateway.application.policy;

public record RollbackPolicySnapshotCommand(long targetSnapshotVersion) { }

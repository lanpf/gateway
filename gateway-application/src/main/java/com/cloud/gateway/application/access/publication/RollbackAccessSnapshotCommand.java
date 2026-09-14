package com.cloud.gateway.application.access.publication;

public record RollbackAccessSnapshotCommand(long targetSnapshotVersion) { }

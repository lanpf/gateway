package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;

public record RollbackAccessSnapshotCommand(SnapshotVersion targetSnapshotVersion) { }

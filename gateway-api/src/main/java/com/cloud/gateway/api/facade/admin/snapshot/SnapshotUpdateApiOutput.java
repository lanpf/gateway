package com.cloud.gateway.api.facade.admin.snapshot;

import java.time.Instant;

/** Snapshot facts after publication or rollback. */
public record SnapshotUpdateApiOutput(long snapshotVersion, String contentHash, Instant publishedAt) { }

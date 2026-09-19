package com.cloud.gateway.api.facade.admin.snapshot;

import java.time.Instant;

/** Currently effective snapshot facts. */
public record SnapshotApiView(long snapshotVersion, String contentHash, Instant publishedAt) { }

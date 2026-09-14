package com.cloud.gateway.api.facade.admin.snapshot;

import java.time.Instant;

/**
 * Snapshot fact after publish/rollback and for status queries; both operations end in this shape.
 * publishedAt is the business time assigned at publication, not transport time.
 */
public record SnapshotApiResponse(Long snapshotVersion, String contentHash, Instant publishedAt) { }

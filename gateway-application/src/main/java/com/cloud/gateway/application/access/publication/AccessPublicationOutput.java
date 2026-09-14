package com.cloud.gateway.application.access.publication;

import java.time.Instant;

/** Snapshot fact after publish/rollback; publishedAt is the business time of publication. */
public record AccessPublicationOutput(long snapshotVersion, String contentHash, Instant publishedAt) { }

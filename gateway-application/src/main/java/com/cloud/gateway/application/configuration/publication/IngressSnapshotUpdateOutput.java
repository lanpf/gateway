package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;

import java.time.Instant;

/** Snapshot fact after publish/rollback; publishedAt is the business time of publication. */
public record IngressSnapshotUpdateOutput(SnapshotVersion snapshotVersion, String contentHash, Instant publishedAt) { }

package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.ingress.snapshot.Revision;

/**
 * The snapshot currently in effect together with the source revision it was read at. The two are
 * distinct axes and are captured together on purpose: {@code snapshot.version()} is the global
 * publication version carried inside the snapshot, while {@code sourceRevision} identifies the
 * remote representation and is the token {@link AccessSnapshotPublisher#compareAndSet} checks.
 */
public record RevisionedAccessSnapshot(IngressSnapshot snapshot, Revision sourceRevision) { }

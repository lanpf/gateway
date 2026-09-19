package com.cloud.gateway.application.configuration.publication;

/**
 * Captures the snapshot currently in effect — it never creates one, publication owns creation.
 * A request captures exactly once so every stage of that request observes one version; this is the
 * {@code snapshotCapture} capability of the domain model.
 */
public interface AccessSnapshotCapturer {
    RevisionedAccessSnapshot capture();
}

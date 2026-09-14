package com.cloud.gateway.application.access.publication;

public interface AccessSnapshotProvider {
    VersionedAccessSnapshot current();
}

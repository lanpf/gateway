package com.cloud.gateway.application.access.publication;

import com.cloud.gateway.domain.access.IngressAccessSnapshot;

public record VersionedAccessSnapshot(IngressAccessSnapshot snapshot, String sourceRevision) { }

package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;

public interface AccessSnapshotHasher {
    String hash(IngressContent content);
}

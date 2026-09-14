package com.cloud.gateway.application.access.publication;

import com.cloud.gateway.domain.access.IngressAccessContent;

public interface AccessSnapshotHasher {
    String hash(IngressAccessContent content);
}

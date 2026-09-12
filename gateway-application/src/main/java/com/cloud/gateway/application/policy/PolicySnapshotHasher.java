package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.access.IngressAccessContent;

public interface PolicySnapshotHasher {
    String hash(IngressAccessContent content);
}

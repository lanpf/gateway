package com.cloud.gateway.api.facade.admin.snapshot;

import com.cloud.framework.core.Result;

/** Currently effective published snapshot; always available, including read-only deployments. */
public interface IngressAccessSnapshotQueryFacade {
    Result<SnapshotApiResponse> get();
}

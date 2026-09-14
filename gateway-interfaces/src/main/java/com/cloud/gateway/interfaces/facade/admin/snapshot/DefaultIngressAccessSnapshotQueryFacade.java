package com.cloud.gateway.interfaces.facade.admin.snapshot;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.snapshot.IngressAccessSnapshotQueryFacade;
import com.cloud.gateway.api.facade.admin.snapshot.SnapshotApiResponse;
import com.cloud.gateway.application.access.publication.AccessSnapshotProvider;
import lombok.RequiredArgsConstructor;

/** Currently effective published snapshot; always available, including read-only deployments. */
@RequiredArgsConstructor
public class DefaultIngressAccessSnapshotQueryFacade implements IngressAccessSnapshotQueryFacade {

    private final com.cloud.gateway.application.access.publication.AccessSnapshotProvider provider;

    @Override public Result<SnapshotApiResponse> get() {
        var current = provider.current().snapshot();
        return Result.success(new SnapshotApiResponse(current.version().value(), current.contentHash(),
                current.publishedAt()));
    }
}

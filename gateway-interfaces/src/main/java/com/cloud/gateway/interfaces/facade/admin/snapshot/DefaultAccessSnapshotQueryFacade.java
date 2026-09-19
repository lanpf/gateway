package com.cloud.gateway.interfaces.facade.admin.snapshot;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.snapshot.AccessSnapshotQueryFacade;
import com.cloud.gateway.api.facade.admin.snapshot.SnapshotApiView;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultAccessSnapshotQueryFacade implements AccessSnapshotQueryFacade {
    private final AccessSnapshotCapturer provider;
    private final AccessSnapshotApiMapper mapper;

    @Override public Result<SnapshotApiView> get() {
        return Result.success(mapper.toView(provider.capture().snapshot()));
    }
}

package com.cloud.gateway.interfaces.facade.admin.snapshot;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.snapshot.IngressAccessSnapshotCommandFacade;
import com.cloud.gateway.api.facade.admin.snapshot.ReleaseSnapshotApiCommand;
import com.cloud.gateway.api.facade.admin.snapshot.RollbackSnapshotApiCommand;
import com.cloud.gateway.api.facade.admin.snapshot.SnapshotApiResponse;
import com.cloud.gateway.application.access.publication.AccessSnapshotCommandService;
import com.cloud.gateway.application.access.publication.PublishAccessSnapshotCommand;
import com.cloud.gateway.application.access.publication.RollbackAccessSnapshotCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

/** Delegates explicit publication and recovery to the snapshot use cases. */
@Validated
@RequiredArgsConstructor
public class DefaultIngressAccessSnapshotCommandFacade implements IngressAccessSnapshotCommandFacade {

    private final com.cloud.gateway.application.access.publication.AccessSnapshotCommandService snapshotService;

    @Override public Result<SnapshotApiResponse> release(ReleaseSnapshotApiCommand command) {
        return Result.success(response(snapshotService.publish(new com.cloud.gateway.application.access.publication.PublishAccessSnapshotCommand())));
    }

    @Override public Result<SnapshotApiResponse> rollback(RollbackSnapshotApiCommand command) {
        return Result.success(response(snapshotService.rollback(new com.cloud.gateway.application.access.publication.RollbackAccessSnapshotCommand(
                command.targetSnapshotVersion()))));
    }

    private static SnapshotApiResponse response(com.cloud.gateway.application.access.publication.AccessPublicationOutput output) {
        return new SnapshotApiResponse(output.snapshotVersion(), output.contentHash(), output.publishedAt());
    }
}

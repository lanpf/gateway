package com.cloud.gateway.interfaces.facade.admin.snapshot;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.snapshot.*;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Validated
@RequiredArgsConstructor
public class DefaultAccessSnapshotCommandFacade implements AccessSnapshotCommandFacade {
    private final AccessSnapshotCommandService snapshotService;
    private final AccessSnapshotApiMapper mapper;

    @Override public Result<SnapshotUpdateApiOutput> release(ReleaseSnapshotApiCommand command) {
        return Result.success(mapper.toOutput(snapshotService.publish(mapper.toCommand(command))));
    }
    @Override public Result<SnapshotUpdateApiOutput> rollback(RollbackSnapshotApiCommand command) {
        return Result.success(mapper.toOutput(snapshotService.rollback(mapper.toCommand(command))));
    }
}

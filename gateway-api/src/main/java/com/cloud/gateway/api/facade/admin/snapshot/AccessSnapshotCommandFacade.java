package com.cloud.gateway.api.facade.admin.snapshot;

import com.cloud.framework.core.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Explicit publication and recovery of the whole access snapshot. Assembled only when a writable
 * definition store exists; read-only deployments expose the query facade alone.
 */
public interface AccessSnapshotCommandFacade {
    Result<SnapshotUpdateApiOutput> release(@NotNull @Valid ReleaseSnapshotApiCommand command);

    Result<SnapshotUpdateApiOutput> rollback(@NotNull @Valid RollbackSnapshotApiCommand command);
}

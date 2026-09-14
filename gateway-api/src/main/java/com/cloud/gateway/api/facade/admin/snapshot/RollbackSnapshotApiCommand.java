package com.cloud.gateway.api.facade.admin.snapshot;

import com.cloud.framework.core.Request;
import jakarta.validation.constraints.Positive;

public record RollbackSnapshotApiCommand(
        @Positive long targetSnapshotVersion
) implements Request { }

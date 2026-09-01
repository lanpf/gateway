package com.cloud.gateway.api.policy;

import com.cloud.framework.core.Request;
import jakarta.validation.constraints.PositiveOrZero;

public record RollbackPolicySnapshotApiCommand(
        @PositiveOrZero long targetSnapshotVersion
) implements Request { }

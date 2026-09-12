package com.cloud.gateway.api.facade;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.policy.ReleaseSnapshotApiCommand;
import com.cloud.gateway.api.policy.SnapshotReleaseApiCommandOutput;
import com.cloud.gateway.api.policy.RollbackSnapshotApiCommand;
import com.cloud.gateway.api.policy.SaveAppApiCommand;
import com.cloud.gateway.api.policy.SaveClientApiCommand;
import com.cloud.gateway.api.policy.SaveEndpointApiCommand;
import com.cloud.gateway.api.policy.SavePolicyCompositionApiCommand;
import com.cloud.gateway.api.policy.SnapshotRollbackApiCommandOutput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface IngressAccessCommandFacade {
    Result<Void> savePolicyConfiguration(@NotNull @Valid SavePolicyCompositionApiCommand command);
    Result<Void> saveApp(@NotNull @Valid SaveAppApiCommand command);
    Result<Void> saveClient(@NotNull @Valid SaveClientApiCommand command);
    Result<Void> saveEndpoint(@NotNull @Valid SaveEndpointApiCommand command);
    Result<SnapshotReleaseApiCommandOutput> releaseSnapshot(@NotNull @Valid ReleaseSnapshotApiCommand command);
    Result<SnapshotRollbackApiCommandOutput> rollbackSnapshot(@NotNull @Valid RollbackSnapshotApiCommand command);
}

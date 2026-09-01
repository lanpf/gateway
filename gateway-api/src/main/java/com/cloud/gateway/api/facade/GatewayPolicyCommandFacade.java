package com.cloud.gateway.api.facade;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.policy.PolicyPublicationApiCommandOutput;
import com.cloud.gateway.api.policy.PublishPolicySnapshotApiCommand;
import com.cloud.gateway.api.policy.RollbackPolicySnapshotApiCommand;
import com.cloud.gateway.api.policy.SaveChannelAccessPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveChannelCredentialPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveClientAppPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveRouteAccessPolicyApiCommand;
import jakarta.validation.Valid;

public interface GatewayPolicyCommandFacade {
    Result<Void> saveClientAppPolicy(@Valid SaveClientAppPolicyApiCommand command);
    Result<Void> saveChannelAccessPolicy(@Valid SaveChannelAccessPolicyApiCommand command);
    Result<Void> saveChannelCredentialPolicy(@Valid SaveChannelCredentialPolicyApiCommand command);
    Result<Void> saveRouteAccessPolicy(@Valid SaveRouteAccessPolicyApiCommand command);
    Result<PolicyPublicationApiCommandOutput> publishPolicySnapshot(@Valid PublishPolicySnapshotApiCommand command);
    Result<PolicyPublicationApiCommandOutput> rollbackPolicySnapshot(@Valid RollbackPolicySnapshotApiCommand command);
}

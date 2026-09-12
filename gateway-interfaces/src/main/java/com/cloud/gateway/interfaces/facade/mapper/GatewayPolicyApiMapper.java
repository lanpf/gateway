package com.cloud.gateway.interfaces.facade.mapper;

import com.cloud.gateway.api.policy.SnapshotReleaseApiCommandOutput;
import com.cloud.gateway.api.policy.RollbackSnapshotApiCommand;
import com.cloud.gateway.api.policy.ReleaseSnapshotApiCommand;
import com.cloud.gateway.api.policy.SaveClientApiCommand;
import com.cloud.gateway.api.policy.SaveAppApiCommand;
import com.cloud.gateway.api.policy.SaveEndpointApiCommand;
import com.cloud.gateway.application.policy.PolicyPublicationOutput;
import com.cloud.gateway.application.policy.RollbackPolicySnapshotCommand;
import com.cloud.gateway.application.policy.PublishPolicySnapshotCommand;
import com.cloud.gateway.application.policy.SaveClientAccessPolicyCommand;
import com.cloud.gateway.application.policy.SaveClientCredentialPolicyCommand;
import com.cloud.gateway.application.policy.SaveAppPolicyCommand;
import com.cloud.gateway.application.policy.SaveEndpointAccessPolicyCommand;

/** API/application conversion contract, independent of the mapping implementation. */
public interface GatewayPolicyApiMapper {
    SaveAppPolicyCommand toCommand(SaveAppApiCommand source);
    SaveClientAccessPolicyCommand toCommand(SaveClientApiCommand source);
    SaveClientCredentialPolicyCommand toCommand(SaveClientCredentialPolicyApiCommand source);
    SaveEndpointAccessPolicyCommand toCommand(SaveEndpointApiCommand source);
    SnapshotReleaseApiCommandOutput toApiOutput(PolicyPublicationOutput source);
    RollbackPolicySnapshotCommand toCommand(RollbackSnapshotApiCommand source);
    PublishPolicySnapshotCommand toCommand(ReleaseSnapshotApiCommand source);
}

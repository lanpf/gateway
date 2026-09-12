package com.cloud.gateway.interfaces.rest;

import com.cloud.gateway.api.policy.*;

public interface GatewayPolicyAdminRestMapper {
    SaveAppApiCommand toCommand(GatewayPolicyAdminRequest.SaveAppPolicy source);
    SaveClientApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientAccessPolicy source);
    SaveClientCredentialPolicyApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientCredentialPolicy source);
    SaveEndpointApiCommand toCommand(GatewayPolicyAdminRequest.SaveEndpointAccessPolicy source);
    RollbackSnapshotApiCommand toCommand(GatewayPolicyAdminRequest.RollbackPolicySnapshot source);
    ReleaseSnapshotApiCommand toCommand(GatewayPolicyAdminRequest.PublishPolicySnapshot source);
}

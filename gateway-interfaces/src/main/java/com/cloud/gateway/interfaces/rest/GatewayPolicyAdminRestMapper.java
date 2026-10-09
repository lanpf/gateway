package com.cloud.gateway.interfaces.rest;

import com.cloud.gateway.api.facade.admin.definition.*;
import com.cloud.gateway.api.facade.admin.snapshot.*;

/** HTTP request to API command conversion contract; implementations contain no access rules. */
public interface GatewayPolicyAdminRestMapper {
    SaveCapabilityPolicyConfigApiCommand toCommand(GatewayPolicyAdminRequest.SaveCapabilityPolicyConfig source);
    SaveAppApiCommand toCommand(GatewayPolicyAdminRequest.SaveAppAccess source);
    SaveClientApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientAccess source);
    SaveClientCredentialApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientCredential source);
    SaveEndpointApiCommand toCommand(GatewayPolicyAdminRequest.SaveEndpointAccess source);
    SetAppStatusApiCommand toCommand(GatewayPolicyAdminRequest.SetAppStatus source);
    SetClientStatusApiCommand toCommand(GatewayPolicyAdminRequest.SetClientStatus source);
    SetEndpointStatusApiCommand toCommand(GatewayPolicyAdminRequest.SetEndpointStatus source);
    RollbackSnapshotApiCommand toCommand(GatewayPolicyAdminRequest.RollbackAccessSnapshot source);
    ReleaseSnapshotApiCommand toCommand(GatewayPolicyAdminRequest.PublishAccessSnapshot source);
}

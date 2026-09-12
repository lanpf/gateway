package com.cloud.gateway.interfaces.rest.mapstruct;

import com.cloud.gateway.api.policy.*;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminRequest;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminRestMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PolicyAdminMapperConfig.class)
public interface GatewayPolicyAdminRestMapStructMapper extends GatewayPolicyAdminRestMapper {
    @Override
    @Mapping(target = "appId", source = "targetAppId")
    SaveAppApiCommand toCommand(GatewayPolicyAdminRequest.SaveAppPolicy source);

    @Override
    SaveClientApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientAccessPolicy source);

    @Override
    SaveClientCredentialPolicyApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientCredentialPolicy source);

    @Override
    SaveEndpointApiCommand toCommand(GatewayPolicyAdminRequest.SaveEndpointAccessPolicy source);

    @Override
    RollbackSnapshotApiCommand toCommand(GatewayPolicyAdminRequest.RollbackPolicySnapshot source);

    @Override
    ReleaseSnapshotApiCommand toCommand(GatewayPolicyAdminRequest.PublishPolicySnapshot source);
}

package com.cloud.gateway.interfaces.rest;

import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_CHANNEL_ACCESS;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_CHANNEL_CREDENTIAL;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_CLIENT_APP;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_ROUTE_ACCESS;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_SNAPSHOT_ROLLBACK;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_SNAPSHOT_PUBLISH;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.GatewayPolicyCommandFacade;
import com.cloud.gateway.api.policy.PolicyPublicationApiCommandOutput;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnBean(GatewayPolicyCommandFacade.class)
public class GatewayPolicyAdminController {
    private final GatewayPolicyCommandFacade facade;

    @PutMapping(ADMIN_POLICIES_CLIENT_APP)
    public Result<Void> saveClientAppPolicy(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveClientAppPolicy request) {
        return facade.saveClientAppPolicy(GatewayPolicyAdminRestMapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_CHANNEL_ACCESS)
    public Result<Void> saveChannelAccessPolicy(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveChannelAccessPolicy request) {
        return facade.saveChannelAccessPolicy(GatewayPolicyAdminRestMapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_CHANNEL_CREDENTIAL)
    public Result<Void> saveChannelCredentialPolicy(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveChannelCredentialPolicy request) {
        return facade.saveChannelCredentialPolicy(GatewayPolicyAdminRestMapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_ROUTE_ACCESS)
    public Result<Void> saveRouteAccessPolicy(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveRouteAccessPolicy request) {
        return facade.saveRouteAccessPolicy(GatewayPolicyAdminRestMapper.toCommand(request));
    }

    @PostMapping(ADMIN_SNAPSHOT_PUBLISH)
    public Result<PolicyPublicationApiCommandOutput> publishPolicySnapshot(
            @Valid @RequestBody GatewayPolicyAdminRequest.PublishPolicySnapshot request) {
        return facade.publishPolicySnapshot(GatewayPolicyAdminRestMapper.toCommand(request));
    }

    @PostMapping(ADMIN_SNAPSHOT_ROLLBACK)
    public Result<PolicyPublicationApiCommandOutput> rollbackPolicySnapshot(
            @Valid @RequestBody GatewayPolicyAdminRequest.RollbackPolicySnapshot request) {
        return facade.rollbackPolicySnapshot(GatewayPolicyAdminRestMapper.toCommand(request));
    }
}

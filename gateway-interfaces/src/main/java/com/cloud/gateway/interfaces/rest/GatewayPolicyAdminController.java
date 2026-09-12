package com.cloud.gateway.interfaces.rest;

import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_APP;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_CLIENT_ACCESS;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_CLIENT_CREDENTIAL;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_ENDPOINT_ACCESS;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICY_CONFIGURATION;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_SNAPSHOT_PUBLISH;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_SNAPSHOT_ROLLBACK;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.IngressAccessCommandFacade;
import com.cloud.gateway.api.policy.SnapshotReleaseApiCommandOutput;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnBean(IngressAccessCommandFacade.class)
public class GatewayPolicyAdminController {
    private final IngressAccessCommandFacade facade;
    private final GatewayPolicyAdminRestMapper adminMapper;
    private final PolicyCompositionRestMapper policyConfigurationMapper;

    @PutMapping(ADMIN_POLICY_CONFIGURATION)
    public Result<Void> savePolicyConfiguration(
            @Valid @RequestBody GatewayPolicyAdminRequest.SavePolicyComposition request) {
        return facade.savePolicyConfiguration(policyConfigurationMapper.toCommand(request));
    }


    @PutMapping(ADMIN_POLICIES_APP)
    public Result<Void> saveAppPolicy(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveAppPolicy request) {
        return facade.saveApp(adminMapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_CLIENT_ACCESS)
    public Result<Void> saveClientAccessPolicy(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveClientAccessPolicy request) {
        return facade.saveClient(adminMapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_CLIENT_CREDENTIAL)
    public Result<Void> saveClientCredentialPolicy(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveClientCredentialPolicy request) {
        return facade.saveClientCredentialPolicy(adminMapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_ENDPOINT_ACCESS)
    public Result<Void> saveEndpointAccessPolicy(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveEndpointAccessPolicy request) {
        return facade.saveEndpoint(adminMapper.toCommand(request));
    }

    @PostMapping(ADMIN_SNAPSHOT_PUBLISH)
    public Result<SnapshotReleaseApiCommandOutput> publishPolicySnapshot(
            @Valid @RequestBody GatewayPolicyAdminRequest.PublishPolicySnapshot request) {
        return facade.releaseSnapshot(adminMapper.toCommand(request));
    }

    @PostMapping(ADMIN_SNAPSHOT_ROLLBACK)
    public Result<SnapshotReleaseApiCommandOutput> rollbackPolicySnapshot(
            @Valid @RequestBody GatewayPolicyAdminRequest.RollbackPolicySnapshot request) {
        return facade.rollbackSnapshot(adminMapper.toCommand(request));
    }
}

package com.cloud.gateway.interfaces.facade;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.IngressAccessCommandFacade;
import com.cloud.gateway.api.policy.ReleaseSnapshotApiCommand;
import com.cloud.gateway.api.policy.SnapshotReleaseApiCommandOutput;
import com.cloud.gateway.api.policy.RollbackSnapshotApiCommand;
import com.cloud.gateway.api.policy.SaveAppApiCommand;
import com.cloud.gateway.api.policy.SaveClientApiCommand;
import com.cloud.gateway.api.policy.SaveEndpointApiCommand;
import com.cloud.gateway.api.policy.SavePolicyCompositionApiCommand;
import com.cloud.gateway.application.policy.ClientAccessPolicyCommandService;
import com.cloud.gateway.application.policy.ClientCredentialPolicyCommandService;
import com.cloud.gateway.application.policy.AppPolicyCommandService;
import com.cloud.gateway.application.policy.PolicySnapshotCommandService;
import com.cloud.gateway.application.policy.EndpointAccessPolicyCommandService;
import com.cloud.gateway.application.policy.PolicyCompositionCommandService;
import com.cloud.gateway.interfaces.facade.mapper.GatewayPolicyApiMapper;
import com.cloud.gateway.interfaces.facade.mapper.PolicyCompositionApiMapper;
import lombok.RequiredArgsConstructor;

@org.springframework.validation.annotation.Validated
@RequiredArgsConstructor
public class DefaultIngressAccessCommandFacade implements IngressAccessCommandFacade {
    private final AppPolicyCommandService appService;
    private final ClientAccessPolicyCommandService clientAccessService;
    private final ClientCredentialPolicyCommandService clientCredentialService;
    private final EndpointAccessPolicyCommandService endpointAccessService;
    private final PolicySnapshotCommandService snapshotService;
    private final GatewayPolicyApiMapper mapper;
    private final PolicyCompositionCommandService policyConfigurationService;
    private final PolicyCompositionApiMapper policyConfigurationMapper;

    @Override public Result<Void> savePolicyConfiguration(SavePolicyCompositionApiCommand command) {
        policyConfigurationService.save(policyConfigurationMapper.toCommand(command));
        return Result.success();
    }


    @Override public Result<Void> saveApp(SaveAppApiCommand command) {
        appService.save(mapper.toCommand(command));
        return Result.success();
    }
    @Override public Result<Void> saveClient(SaveClientApiCommand command) {
        clientAccessService.execute(mapper.toCommand(command));
        return Result.success();
    }
    @Override public Result<Void> saveClientCredentialPolicy(SaveClientCredentialPolicyApiCommand command) {
        clientCredentialService.save(mapper.toCommand(command));
        return Result.success();
    }
    @Override public Result<Void> saveEndpoint(SaveEndpointApiCommand command) {
        endpointAccessService.save(mapper.toCommand(command));
        return Result.success();
    }
    @Override public Result<SnapshotReleaseApiCommandOutput> releaseSnapshot(
            ReleaseSnapshotApiCommand command) {
        return Result.success(mapper.toApiOutput(snapshotService.publish(mapper.toCommand(command))));
    }
    @Override public Result<SnapshotReleaseApiCommandOutput> rollbackSnapshot(
            RollbackSnapshotApiCommand command) {
        return Result.success(mapper.toApiOutput(snapshotService.rollback(mapper.toCommand(command))));
    }
}

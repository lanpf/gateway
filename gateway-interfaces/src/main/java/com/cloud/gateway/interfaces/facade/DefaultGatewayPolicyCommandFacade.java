package com.cloud.gateway.interfaces.facade;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.GatewayPolicyCommandFacade;
import com.cloud.gateway.api.policy.PolicyPublicationApiCommandOutput;
import com.cloud.gateway.api.policy.PublishPolicySnapshotApiCommand;
import com.cloud.gateway.api.policy.RollbackPolicySnapshotApiCommand;
import com.cloud.gateway.api.policy.SaveChannelAccessPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveChannelCredentialPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveClientAppPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveRouteAccessPolicyApiCommand;
import com.cloud.gateway.application.policy.ChannelAccessPolicyCommandService;
import com.cloud.gateway.application.policy.ChannelCredentialPolicyCommandService;
import com.cloud.gateway.application.policy.ClientAppPolicyCommandService;
import com.cloud.gateway.application.policy.PolicySnapshotCommandService;
import com.cloud.gateway.application.policy.RouteAccessPolicyCommandService;
import com.cloud.gateway.interfaces.facade.mapper.GatewayPolicyApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Validated
@RequiredArgsConstructor
public class DefaultGatewayPolicyCommandFacade implements GatewayPolicyCommandFacade {
    private final ClientAppPolicyCommandService clientAppService;
    private final ChannelAccessPolicyCommandService channelAccessService;
    private final ChannelCredentialPolicyCommandService channelCredentialService;
    private final RouteAccessPolicyCommandService routeAccessService;
    private final PolicySnapshotCommandService snapshotService;
    private final GatewayPolicyApiMapper mapper;

    @Override public Result<Void> saveClientAppPolicy(SaveClientAppPolicyApiCommand command) {
        clientAppService.save(mapper.toCommand(command));
        return Result.success();
    }
    @Override public Result<Void> saveChannelAccessPolicy(SaveChannelAccessPolicyApiCommand command) {
        channelAccessService.execute(mapper.toCommand(command));
        return Result.success();
    }
    @Override public Result<Void> saveChannelCredentialPolicy(SaveChannelCredentialPolicyApiCommand command) {
        channelCredentialService.save(mapper.toCommand(command));
        return Result.success();
    }
    @Override public Result<Void> saveRouteAccessPolicy(SaveRouteAccessPolicyApiCommand command) {
        routeAccessService.save(mapper.toCommand(command));
        return Result.success();
    }
    @Override public Result<PolicyPublicationApiCommandOutput> publishPolicySnapshot(
            PublishPolicySnapshotApiCommand command) {
        return Result.success(mapper.toApiOutput(snapshotService.publish(mapper.toCommand(command))));
    }
    @Override public Result<PolicyPublicationApiCommandOutput> rollbackPolicySnapshot(
            RollbackPolicySnapshotApiCommand command) {
        return Result.success(mapper.toApiOutput(snapshotService.rollback(mapper.toCommand(command))));
    }
}

package com.cloud.gateway.interfaces.facade.admin.definition;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.definition.DefinitionSaveApiOutput;
import com.cloud.gateway.api.facade.admin.definition.IngressAccessDefinitionCommandFacade;
import com.cloud.gateway.api.facade.admin.definition.SaveAppApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveClientApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveClientCredentialApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveEndpointApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SetAppStatusApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SetClientStatusApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SetEndpointStatusApiCommand;
import com.cloud.gateway.application.access.editing.AppAccessCommandService;
import com.cloud.gateway.application.access.editing.ClientAccessCommandService;
import com.cloud.gateway.application.access.editing.ClientCredentialCommandService;
import com.cloud.gateway.application.access.editing.EndpointAccessCommandService;
import com.cloud.gateway.application.access.editing.SaveAppAccessCommand;
import com.cloud.gateway.application.access.editing.SaveClientAccessCommand;
import com.cloud.gateway.application.access.editing.SaveClientCredentialCommand;
import com.cloud.gateway.application.access.editing.SaveEndpointAccessCommand;
import com.cloud.gateway.application.access.editing.SetAppStatusCommand;
import com.cloud.gateway.application.access.editing.SetClientStatusCommand;
import com.cloud.gateway.application.access.editing.SetEndpointStatusCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

/** Delegates the definition commands to the per-aggregate access use cases. */
@Validated
@RequiredArgsConstructor
public class DefaultIngressAccessDefinitionCommandFacade implements IngressAccessDefinitionCommandFacade {
    private final com.cloud.gateway.application.access.editing.AppAccessCommandService appAccessService;
    private final com.cloud.gateway.application.access.editing.ClientAccessCommandService clientAccessService;
    private final com.cloud.gateway.application.access.editing.ClientCredentialCommandService clientCredentialService;
    private final com.cloud.gateway.application.access.editing.EndpointAccessCommandService endpointAccessService;

    @Override public Result<DefinitionSaveApiOutput> saveApp(SaveAppApiCommand command) {
        String revision = appAccessService.save(new com.cloud.gateway.application.access.editing.SaveAppAccessCommand(command.expectedSourceRevision(),
                AccessDefinitionApiMapper.appId(command.appId()), command.platform(), command.allowedOrigins()));
        return Result.success(new DefinitionSaveApiOutput(revision));
    }

    @Override public Result<DefinitionSaveApiOutput> saveClient(SaveClientApiCommand command) {
        String revision = clientAccessService.save(new com.cloud.gateway.application.access.editing.SaveClientAccessCommand(command.expectedSourceRevision(),
                AccessDefinitionApiMapper.clientId(command.clientId()),
                AccessDefinitionApiMapper.appIds(command.allowedAppIds())));
        return Result.success(new DefinitionSaveApiOutput(revision));
    }

    @Override public Result<DefinitionSaveApiOutput> saveClientCredential(SaveClientCredentialApiCommand command) {
        String revision = clientCredentialService.save(new com.cloud.gateway.application.access.editing.SaveClientCredentialCommand(
                command.expectedSourceRevision(), AccessDefinitionApiMapper.clientId(command.clientId()),
                AccessDefinitionApiMapper.credentials(command.activeCredential(), command.graceCredential())));
        return Result.success(new DefinitionSaveApiOutput(revision));
    }

    @Override public Result<DefinitionSaveApiOutput> saveEndpoint(SaveEndpointApiCommand command) {
        String revision = endpointAccessService.save(new com.cloud.gateway.application.access.editing.SaveEndpointAccessCommand(
                command.expectedSourceRevision(), AccessDefinitionApiMapper.endpointId(command.endpointId()),
                new com.cloud.gateway.domain.endpoint.ServiceId(command.serviceId()),
                new com.cloud.gateway.domain.endpoint.RouteId(command.routeId()),
                command.matchers().stream()
                        .map(matcher -> new com.cloud.gateway.domain.endpoint.EndpointMatcher(
                                matcher.httpMethod(), matcher.pathPattern()))
                        .toList(),
                command.tags(), AccessDefinitionApiMapper.endpointAccess(command)));
        return Result.success(new DefinitionSaveApiOutput(revision));
    }

    @Override public Result<DefinitionSaveApiOutput> setAppStatus(SetAppStatusApiCommand command) {
        String revision = appAccessService.setAppStatus(new com.cloud.gateway.application.access.editing.SetAppStatusCommand(command.expectedSourceRevision(),
                AccessDefinitionApiMapper.appId(command.appId()),
                AccessDefinitionApiMapper.appStatus(command.status())));
        return Result.success(new DefinitionSaveApiOutput(revision));
    }

    @Override public Result<DefinitionSaveApiOutput> setClientStatus(SetClientStatusApiCommand command) {
        String revision = clientAccessService.setClientStatus(new com.cloud.gateway.application.access.editing.SetClientStatusCommand(
                command.expectedSourceRevision(), AccessDefinitionApiMapper.clientId(command.clientId()),
                AccessDefinitionApiMapper.clientStatus(command.status())));
        return Result.success(new DefinitionSaveApiOutput(revision));
    }

    @Override public Result<DefinitionSaveApiOutput> setEndpointStatus(SetEndpointStatusApiCommand command) {
        String revision = endpointAccessService.setEndpointStatus(new com.cloud.gateway.application.access.editing.SetEndpointStatusCommand(
                command.expectedSourceRevision(), AccessDefinitionApiMapper.endpointId(command.endpointId()),
                AccessDefinitionApiMapper.endpointStatus(command.status())));
        return Result.success(new DefinitionSaveApiOutput(revision));
    }
}

package com.cloud.gateway.interfaces.facade.admin.definition;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.definition.*;
import com.cloud.gateway.application.configuration.editing.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Validated
@RequiredArgsConstructor
public class DefaultAccessDefinitionCommandFacade implements AccessDefinitionCommandFacade {
    private final AppAccessCommandService appAccessService;
    private final ClientAccessCommandService clientAccessService;
    private final ClientCredentialCommandService clientCredentialService;
    private final EndpointAccessCommandService endpointAccessService;
    private final AccessDefinitionApiMapper mapper;

    @Override
    public Result<DefinitionSaveApiOutput> saveApp(SaveAppApiCommand command) {
        return Result.success(mapper.saved(appAccessService.save(mapper.toCommand(command))));
    }

    @Override
    public Result<DefinitionSaveApiOutput> saveClient(SaveClientApiCommand command) {
        return Result.success(mapper.saved(clientAccessService.save(mapper.toCommand(command))));
    }

    @Override
    public Result<DefinitionSaveApiOutput> saveClientCredential(SaveClientCredentialApiCommand command) {
        return Result.success(mapper.saved(clientCredentialService.save(mapper.toCommand(command))));
    }

    @Override
    public Result<DefinitionSaveApiOutput> saveEndpoint(SaveEndpointApiCommand command) {
        return Result.success(mapper.saved(endpointAccessService.save(mapper.toCommand(command))));
    }

    @Override
    public Result<DefinitionSaveApiOutput> setAppStatus(SetAppStatusApiCommand command) {
        return Result.success(mapper.saved(appAccessService.setAppStatus(mapper.toCommand(command))));
    }

    @Override
    public Result<DefinitionSaveApiOutput> setClientStatus(SetClientStatusApiCommand command) {
        return Result.success(mapper.saved(clientAccessService.setClientStatus(mapper.toCommand(command))));
    }

    @Override
    public Result<DefinitionSaveApiOutput> setEndpointStatus(SetEndpointStatusApiCommand command) {
        return Result.success(mapper.saved(endpointAccessService.setEndpointStatus(mapper.toCommand(command))));
    }
}

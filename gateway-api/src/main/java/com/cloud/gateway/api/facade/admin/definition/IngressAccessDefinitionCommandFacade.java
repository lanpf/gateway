package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Management commands for the three access definitions. Content upserts never carry status;
 * enable/disable are dedicated transitions. Assembled only when a writable definition store
 * exists; read-only deployments expose the query facade alone.
 */
public interface IngressAccessDefinitionCommandFacade {
    Result<DefinitionSaveApiOutput> saveApp(@NotNull @Valid SaveAppApiCommand command);

    Result<DefinitionSaveApiOutput> saveClient(@NotNull @Valid SaveClientApiCommand command);

    Result<DefinitionSaveApiOutput> saveClientCredential(@NotNull @Valid SaveClientCredentialApiCommand command);

    Result<DefinitionSaveApiOutput> saveEndpoint(@NotNull @Valid SaveEndpointApiCommand command);

    Result<DefinitionSaveApiOutput> setAppStatus(@NotNull @Valid SetAppStatusApiCommand command);

    Result<DefinitionSaveApiOutput> setClientStatus(@NotNull @Valid SetClientStatusApiCommand command);

    Result<DefinitionSaveApiOutput> setEndpointStatus(@NotNull @Valid SetEndpointStatusApiCommand command);
}

package com.cloud.gateway.interfaces.rest;

import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_APP;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICY_CONFIGURATION;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_CLIENT_ACCESS;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_CLIENT_CREDENTIAL;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_ENDPOINT_ACCESS;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.definition.DefinitionSaveApiOutput;
import com.cloud.gateway.api.facade.admin.definition.AccessDefinitionCommandFacade;
import com.cloud.gateway.api.facade.admin.definition.SetAppStatusApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SetClientStatusApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SetEndpointStatusApiCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** Content and status writes of the access definitions; absent in read-only deployments. */
@RestController
@RequiredArgsConstructor
public class GatewayAccessDefinitionCommandController {
    private final AccessDefinitionCommandFacade facade;
    private final GatewayPolicyAdminRestMapper mapper;

    @PutMapping(ADMIN_POLICY_CONFIGURATION)
    public Result<DefinitionSaveApiOutput> saveCapabilityComposition(
            @Valid @ClientRequestBody GatewayPolicyAdminRequest.SaveCapabilityComposition request) {
        return facade.saveCapabilityComposition(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_APP)
    public Result<DefinitionSaveApiOutput> saveApp(
            @Valid @ClientRequestBody GatewayPolicyAdminRequest.SaveAppAccess request) {
        return facade.saveApp(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_CLIENT_ACCESS)
    public Result<DefinitionSaveApiOutput> saveClient(
            @Valid @ClientRequestBody GatewayPolicyAdminRequest.SaveClientAccess request) {
        return facade.saveClient(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_CLIENT_CREDENTIAL)
    public Result<DefinitionSaveApiOutput> saveClientCredential(
            @Valid @ClientRequestBody GatewayPolicyAdminRequest.SaveClientCredential request) {
        return facade.saveClientCredential(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_ENDPOINT_ACCESS)
    public Result<DefinitionSaveApiOutput> saveEndpoint(
            @Valid @ClientRequestBody GatewayPolicyAdminRequest.SaveEndpointAccess request) {
        return facade.saveEndpoint(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_APP + "/status")
    public Result<DefinitionSaveApiOutput> setAppStatus(
            @Valid @ClientRequestBody GatewayPolicyAdminRequest.SetAppStatus request) {
        SetAppStatusApiCommand command = mapper.toCommand(request);
        return facade.setAppStatus(command);
    }

    @PutMapping(ADMIN_POLICIES_CLIENT_ACCESS + "/status")
    public Result<DefinitionSaveApiOutput> setClientStatus(
            @Valid @ClientRequestBody GatewayPolicyAdminRequest.SetClientStatus request) {
        return facade.setClientStatus(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_ENDPOINT_ACCESS + "/status")
    public Result<DefinitionSaveApiOutput> setEndpointStatus(
            @Valid @ClientRequestBody GatewayPolicyAdminRequest.SetEndpointStatus request) {
        return facade.setEndpointStatus(mapper.toCommand(request));
    }
}

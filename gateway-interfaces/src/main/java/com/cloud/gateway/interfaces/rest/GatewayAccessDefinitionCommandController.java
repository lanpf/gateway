package com.cloud.gateway.interfaces.rest;

import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_APP;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_CLIENT_ACCESS;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_CLIENT_CREDENTIAL;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_ENDPOINT_ACCESS;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.definition.DefinitionSaveApiOutput;
import com.cloud.gateway.api.facade.admin.definition.IngressAccessDefinitionCommandFacade;
import com.cloud.gateway.api.facade.admin.definition.SetAppStatusApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SetClientStatusApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SetEndpointStatusApiCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Content and status writes of the access definitions; absent in read-only deployments. */
@RestController
@RequiredArgsConstructor
@ConditionalOnBean(IngressAccessDefinitionCommandFacade.class)
public class GatewayAccessDefinitionCommandController {
    private final IngressAccessDefinitionCommandFacade facade;
    private final GatewayPolicyAdminRestMapper mapper;

    @PutMapping(ADMIN_POLICIES_APP)
    public Result<DefinitionSaveApiOutput> saveApp(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveAppAccess request) {
        return facade.saveApp(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_CLIENT_ACCESS)
    public Result<DefinitionSaveApiOutput> saveClient(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveClientAccess request) {
        return facade.saveClient(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_CLIENT_CREDENTIAL)
    public Result<DefinitionSaveApiOutput> saveClientCredential(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveClientCredential request) {
        return facade.saveClientCredential(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_ENDPOINT_ACCESS)
    public Result<DefinitionSaveApiOutput> saveEndpoint(
            @Valid @RequestBody GatewayPolicyAdminRequest.SaveEndpointAccess request) {
        return facade.saveEndpoint(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_APP + "/status")
    public Result<DefinitionSaveApiOutput> setAppStatus(
            @Valid @RequestBody GatewayPolicyAdminRequest.SetAppStatus request) {
        SetAppStatusApiCommand command = mapper.toCommand(request);
        return facade.setAppStatus(command);
    }

    @PutMapping(ADMIN_POLICIES_CLIENT_ACCESS + "/status")
    public Result<DefinitionSaveApiOutput> setClientStatus(
            @Valid @RequestBody GatewayPolicyAdminRequest.SetClientStatus request) {
        return facade.setClientStatus(mapper.toCommand(request));
    }

    @PutMapping(ADMIN_POLICIES_ENDPOINT_ACCESS + "/status")
    public Result<DefinitionSaveApiOutput> setEndpointStatus(
            @Valid @RequestBody GatewayPolicyAdminRequest.SetEndpointStatus request) {
        return facade.setEndpointStatus(mapper.toCommand(request));
    }
}

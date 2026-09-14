package com.cloud.gateway.interfaces.rest;

import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_APP;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_CLIENT_ACCESS;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICIES_ENDPOINT_ACCESS;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.definition.AppsApiView;
import com.cloud.gateway.api.facade.admin.definition.ClientsApiView;
import com.cloud.gateway.api.facade.admin.definition.EndpointsApiView;
import com.cloud.gateway.api.facade.admin.definition.IngressAccessDefinitionQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Editing state of the access definitions; available in every deployment. */
@RestController
@RequiredArgsConstructor
public class GatewayAccessDefinitionQueryController {
    private final IngressAccessDefinitionQueryFacade queryFacade;

    @GetMapping(ADMIN_POLICIES_APP)
    public Result<AppsApiView> apps() {
        return queryFacade.getApps();
    }

    @GetMapping(ADMIN_POLICIES_CLIENT_ACCESS)
    public Result<ClientsApiView> clients() {
        return queryFacade.getClients();
    }

    @GetMapping(ADMIN_POLICIES_ENDPOINT_ACCESS)
    public Result<EndpointsApiView> endpoints() {
        return queryFacade.getEndpoints();
    }
}

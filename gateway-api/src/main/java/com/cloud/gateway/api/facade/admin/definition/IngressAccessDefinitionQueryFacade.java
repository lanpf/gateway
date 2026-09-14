package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.Result;

/** Editing state of the three access definitions; always available, including read-only deployments. */
public interface IngressAccessDefinitionQueryFacade {
    Result<AppsApiView> getApps();

    Result<ClientsApiView> getClients();

    Result<EndpointsApiView> getEndpoints();
}

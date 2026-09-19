package com.cloud.gateway.interfaces.facade.admin.definition;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.definition.*;
import com.cloud.gateway.application.configuration.definition.AccessDefinitionReader;
import lombok.RequiredArgsConstructor;

/** Projects one observation per query without re-reading individual values. */
@RequiredArgsConstructor
public class DefaultAccessDefinitionQueryFacade implements AccessDefinitionQueryFacade {
    private final AccessDefinitionReader reader;
    private final AccessDefinitionApiMapper mapper;

    @Override public Result<AppsApiView> getApps() {
        return Result.success(mapper.apps(reader.load().apps()));
    }
    @Override public Result<ClientsApiView> getClients() {
        return Result.success(mapper.clients(reader.load().clients()));
    }
    @Override public Result<EndpointsApiView> getEndpoints() {
        return Result.success(mapper.endpoints(reader.load().endpoints()));
    }
}

package com.cloud.gateway.interfaces.facade.admin.definition;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.definition.AppsApiView;
import com.cloud.gateway.api.facade.admin.definition.ClientsApiView;
import com.cloud.gateway.api.facade.admin.definition.EndpointsApiView;
import com.cloud.gateway.api.facade.admin.definition.IngressAccessDefinitionQueryFacade;
import com.cloud.gateway.application.access.definition.AccessDefinitionReader;
import com.cloud.gateway.application.access.definition.ObservedAccessDefinitions;
import lombok.RequiredArgsConstructor;

/** Projects one observation of the access definitions into the editing views. */
@RequiredArgsConstructor
public class DefaultIngressAccessDefinitionQueryFacade implements IngressAccessDefinitionQueryFacade {
    private final com.cloud.gateway.application.access.definition.AccessDefinitionReader reader;

    @Override public Result<AppsApiView> getApps() {
        com.cloud.gateway.application.access.definition.ObservedAccessDefinitions observed = reader.load();
        return Result.success(new AppsApiView(observed.apps().sourceRevision(),
                observed.apps().value().values().stream()
                        .map(AccessDefinitionApiMapper::appView)
                        .collect(java.util.stream.Collectors.toSet())));
    }

    @Override public Result<ClientsApiView> getClients() {
        com.cloud.gateway.application.access.definition.ObservedAccessDefinitions observed = reader.load();
        return Result.success(new ClientsApiView(observed.clients().sourceRevision(),
                observed.clients().value().values().stream()
                        .map(AccessDefinitionApiMapper::clientView)
                        .collect(java.util.stream.Collectors.toSet())));
    }

    @Override public Result<EndpointsApiView> getEndpoints() {
        com.cloud.gateway.application.access.definition.ObservedAccessDefinitions observed = reader.load();
        return Result.success(new EndpointsApiView(observed.endpoints().sourceRevision(),
                observed.endpoints().value().values().stream()
                        .map(AccessDefinitionApiMapper::endpointView)
                        .collect(java.util.stream.Collectors.toSet())));
    }
}

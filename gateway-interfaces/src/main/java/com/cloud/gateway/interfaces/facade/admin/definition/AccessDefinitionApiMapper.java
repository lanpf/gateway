package com.cloud.gateway.interfaces.facade.admin.definition;

import com.cloud.gateway.api.facade.admin.definition.*;
import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.application.configuration.editing.*;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.ingress.snapshot.Revision;

/** API/application commands and editing projections. Domain reconstruction still validates invariants. */
public interface AccessDefinitionApiMapper {
    SaveAppAccessCommand toCommand(SaveAppApiCommand source);
    SaveClientAccessCommand toCommand(SaveClientApiCommand source);
    SaveClientCredentialCommand toCommand(SaveClientCredentialApiCommand source);
    SaveEndpointAccessCommand toCommand(SaveEndpointApiCommand source);
    SetAppStatusCommand toCommand(SetAppStatusApiCommand source);
    SetClientStatusCommand toCommand(SetClientStatusApiCommand source);
    SetEndpointStatusCommand toCommand(SetEndpointStatusApiCommand source);
    DefinitionSaveApiOutput saved(Revision source);
    AppsApiView apps(RevisionedAccessDefinition<AppRegistry> source);
    ClientsApiView clients(RevisionedAccessDefinition<ClientRegistry> source);
    EndpointsApiView endpoints(RevisionedAccessDefinition<EndpointRegistry> source);
}

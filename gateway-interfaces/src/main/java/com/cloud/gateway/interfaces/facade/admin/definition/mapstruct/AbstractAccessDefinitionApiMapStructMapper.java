package com.cloud.gateway.interfaces.facade.admin.definition.mapstruct;

import com.cloud.gateway.api.enums.*;
import com.cloud.gateway.api.facade.admin.definition.*;
import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.application.configuration.editing.*;
import com.cloud.gateway.domain.app.*;
import com.cloud.gateway.domain.client.*;
import com.cloud.gateway.domain.common.AbstractStringEntityId;
import com.cloud.gateway.domain.endpoint.*;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.interfaces.facade.admin.definition.AccessDefinitionApiMapper;
import java.util.Collection;
import java.util.Set;
import com.cloud.framework.core.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public abstract class AbstractAccessDefinitionApiMapStructMapper implements AccessDefinitionApiMapper {
    @Override public abstract SaveAppAccessCommand toCommand(SaveAppApiCommand source);
    @Override public abstract SaveClientAccessCommand toCommand(SaveClientApiCommand source);
    @Override
    @Mapping(target = "credentials", expression = "java(credentials(source.activeCredential(), source.graceCredential()))")
    public abstract SaveClientCredentialCommand toCommand(SaveClientCredentialApiCommand source);

    @Override
    public SaveEndpointAccessCommand toCommand(SaveEndpointApiCommand source) {
        EndpointAccess access = new EndpointAccess(requirement(source.clientVerificationRequirement()),
                requirement(source.subjectAuthenticationRequirement()), source.requiredScopes(), source.requiredAudiences());
        return endpointCommand(source, access);
    }

    @Mapping(target = "clientVerificationRequirement", source = "access.clientVerificationRequirement")
    @Mapping(target = "subjectAuthenticationRequirement", source = "access.subjectAuthenticationRequirement")
    @Mapping(target = "requiredScopes", source = "access.requiredScopes")
    @Mapping(target = "requiredAudiences", source = "access.requiredAudiences")
    protected abstract SaveEndpointAccessCommand endpointCommand(SaveEndpointApiCommand source, EndpointAccess access);

    @Override public abstract SetAppStatusCommand toCommand(SetAppStatusApiCommand source);
    @Override public abstract SetClientStatusCommand toCommand(SetClientStatusApiCommand source);
    @Override public abstract SetEndpointStatusCommand toCommand(SetEndpointStatusApiCommand source);
    @Override
    @Mapping(target = "sourceRevision", source = "value")
    public abstract DefinitionSaveApiOutput saved(Revision source);

    @Override
    @Mapping(target = "apps", source = "value")
    public abstract AppsApiView apps(RevisionedAccessDefinition<AppRegistry> source);
    @Override
    @Mapping(target = "clients", source = "value")
    public abstract ClientsApiView clients(RevisionedAccessDefinition<ClientRegistry> source);
    @Override
    @Mapping(target = "endpoints", source = "value")
    public abstract EndpointsApiView endpoints(RevisionedAccessDefinition<EndpointRegistry> source);

    @Mapping(target = "allowedOrigins", source = "access.allowedOrigins")
    @Mapping(target = "appId", source = "id")
    protected abstract AppsApiView.AppApiView appView(App source);
    @Mapping(target = "allowedAppIds", source = "access.allowedApps")
    @Mapping(target = "activeCredential", source = "access.credentials.activeCredential")
    @Mapping(target = "graceCredential", source = "access.credentials.graceCredential")
    @Mapping(target = "clientId", source = "id")
    protected abstract ClientsApiView.ClientApiView clientView(Client source);
    @Mapping(target = "clientVerificationRequirement", source = "access.clientVerificationRequirement")
    @Mapping(target = "subjectAuthenticationRequirement", source = "access.subjectAuthenticationRequirement")
    @Mapping(target = "requiredScopes", source = "access.requiredScopes")
    @Mapping(target = "requiredAudiences", source = "access.requiredAudiences")
    @Mapping(target = "endpointId", source = "id")
    protected abstract EndpointsApiView.EndpointApiView endpointView(Endpoint source);
    protected abstract EndpointsApiView.MatcherApiView matcherView(EndpointMatcher source);
    // The view type deliberately has no secretRef; no storage DTO is reused for this projection.
    protected abstract ClientsApiView.CredentialApiView credentialView(Credential source);
    protected abstract Set<AppsApiView.AppApiView> appViews(Collection<App> source);
    protected abstract Set<ClientsApiView.ClientApiView> clientViews(Collection<Client> source);
    protected abstract Set<EndpointsApiView.EndpointApiView> endpointViews(Collection<Endpoint> source);

    protected Set<AppsApiView.AppApiView> appRegistry(AppRegistry source) { return appViews(source.values()); }
    protected Set<ClientsApiView.ClientApiView> clientRegistry(ClientRegistry source) { return clientViews(source.values()); }
    protected Set<EndpointsApiView.EndpointApiView> endpointRegistry(EndpointRegistry source) { return endpointViews(source.values()); }
    protected String identifier(AbstractStringEntityId source) { return source == null ? null : source.value(); }
    protected String revisionValue(Revision source) { return source.value(); }
    protected Revision revision(String source) { return new Revision(source); }
    protected AppId appId(String source) { return new AppId(source); }
    protected ClientId clientId(String source) { return new ClientId(source); }
    protected EndpointId endpointId(String source) { return new EndpointId(source); }
    protected ServiceId serviceId(String source) { return new ServiceId(source); }
    protected RouteId routeId(String source) { return new RouteId(source); }
    protected String serviceValue(ServiceId source) { return source == null ? null : source.value(); }
    protected String routeValue(RouteId source) { return source == null ? null : source.value(); }
    protected EndpointMatcher matcher(SaveEndpointApiCommand.MatcherApiCommand source) {
        return new EndpointMatcher(source.httpMethod(), source.pathPattern());
    }
    protected abstract ClientVerificationRequirement requirement(ClientVerificationRequirementApiEnum source);
    protected abstract SubjectAuthenticationRequirement requirement(SubjectAuthenticationRequirementApiEnum source);
    protected abstract SignatureContract contract(SignatureContractApiEnum source);
    protected Credentials credentials(SaveClientCredentialApiCommand.CredentialApiCommand active,
            SaveClientCredentialApiCommand.CredentialApiCommand grace) {
        return new Credentials(credential(active), grace == null ? null : credential(grace));
    }
    private Credential credential(SaveClientCredentialApiCommand.CredentialApiCommand source) {
        return new Credential(new CredentialId(source.credentialId()), source.secretRef(),
                contract(source.signatureContract()), source.validFrom(), source.expiresAt());
    }
}

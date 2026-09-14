package com.cloud.gateway.interfaces.facade.admin.definition;

import com.cloud.gateway.api.enums.AppStatusApiEnum;
import com.cloud.gateway.api.enums.ClientStatusApiEnum;
import com.cloud.gateway.api.enums.ClientVerificationRequirementApiEnum;
import com.cloud.gateway.api.enums.EndpointStatusApiEnum;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;
import com.cloud.gateway.api.enums.SubjectAuthenticationRequirementApiEnum;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import java.util.Set;
import java.util.stream.Collectors;

/** API-to-domain and domain-to-API vocabulary translation for the access definitions. */
final class AccessDefinitionApiMapper {
    private AccessDefinitionApiMapper() { }

    static AppId appId(String value) {
        return new AppId(value);
    }

    static ClientId clientId(String value) {
        return new ClientId(value);
    }

    static EndpointId endpointId(String value) {
        return new EndpointId(value);
    }

    static Set<AppId> appIds(Set<String> values) {
        return values == null ? null : values.stream().map(AppId::new).collect(Collectors.toSet());
    }

    static Set<String> appIdValues(Set<AppId> values) {
        return values == null ? null : values.stream().map(AppId::value).collect(Collectors.toSet());
    }

    static AppStatusApiEnum status(AppStatus status) {
        return AppStatusApiEnum.valueOf(status.name());
    }

    static AppStatus appStatus(AppStatusApiEnum status) {
        return AppStatus.valueOf(status.name());
    }

    static ClientStatusApiEnum status(ClientStatus status) {
        return ClientStatusApiEnum.valueOf(status.name());
    }

    static ClientStatus clientStatus(ClientStatusApiEnum status) {
        return ClientStatus.valueOf(status.name());
    }

    static EndpointStatusApiEnum status(EndpointStatus status) {
        return EndpointStatusApiEnum.valueOf(status.name());
    }

    static EndpointStatus endpointStatus(EndpointStatusApiEnum status) {
        return EndpointStatus.valueOf(status.name());
    }

    static ClientVerificationRequirementApiEnum requirement(ClientVerificationRequirement requirement) {
        return ClientVerificationRequirementApiEnum.valueOf(requirement.name());
    }

    static ClientVerificationRequirement requirement(ClientVerificationRequirementApiEnum requirement) {
        return ClientVerificationRequirement.valueOf(requirement.name());
    }

    static SubjectAuthenticationRequirementApiEnum requirement(SubjectAuthenticationRequirement requirement) {
        return SubjectAuthenticationRequirementApiEnum.valueOf(requirement.name());
    }

    static SubjectAuthenticationRequirement requirement(SubjectAuthenticationRequirementApiEnum requirement) {
        return SubjectAuthenticationRequirement.valueOf(requirement.name());
    }

    static SignatureContractApiEnum contract(SignatureContract contract) {
        return SignatureContractApiEnum.valueOf(contract.name());
    }

    static SignatureContract contract(SignatureContractApiEnum contract) {
        return SignatureContract.valueOf(contract.name());
    }

    /** Credential facts for editing; secretRef is deliberately not part of any view. */
    static com.cloud.gateway.api.facade.admin.definition.ClientsApiView.Credential credentialView(
            Credential credential) {
        return new com.cloud.gateway.api.facade.admin.definition.ClientsApiView.Credential(
                credential.credentialId().value(), contract(credential.signatureContract()),
                credential.validFrom(), credential.expiresAt());
    }

    static java.util.List<com.cloud.gateway.api.facade.admin.definition.ClientsApiView.Credential> credentialViews(
            Credentials credentials) {
        return credentials == null ? java.util.List.of()
                : java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(credentials.activeCredential()),
                        credentials.graceCredentialOptional().stream()).map(AccessDefinitionApiMapper::credentialView)
                        .toList();
    }

    static Credentials credentials(
            com.cloud.gateway.api.facade.admin.definition.SaveClientCredentialApiCommand.Credential active,
            com.cloud.gateway.api.facade.admin.definition.SaveClientCredentialApiCommand.Credential grace) {
        return new Credentials(credential(active), grace == null ? null : credential(grace));
    }

    private static Credential credential(
            com.cloud.gateway.api.facade.admin.definition.SaveClientCredentialApiCommand.Credential source) {
        return new Credential(new com.cloud.gateway.domain.client.CredentialId(source.credentialId()),
                source.secretRef(), contract(source.signatureContract()), source.validFrom(), source.expiresAt());
    }

    static EndpointAccess endpointAccess(
            com.cloud.gateway.api.facade.admin.definition.SaveEndpointApiCommand command) {
        return new EndpointAccess(requirement(command.clientVerificationRequirement()),
                requirement(command.subjectAuthenticationRequirement()),
                command.requiredScopes(), command.requiredAudiences());
    }

    static Endpoint endpoint(
            com.cloud.gateway.api.facade.admin.definition.SaveEndpointApiCommand command) {
        return new Endpoint(endpointId(command.endpointId()), new ServiceId(command.serviceId()),
                new RouteId(command.routeId()),
                command.matchers().stream()
                        .map(matcher -> new EndpointMatcher(matcher.httpMethod(), matcher.pathPattern()))
                        .collect(Collectors.toList()),
                command.tags() == null ? Set.of() : command.tags(),
                EndpointStatus.ENABLED, endpointAccess(command));
    }

    static com.cloud.gateway.api.facade.admin.definition.EndpointsApiView.Endpoint endpointView(Endpoint endpoint) {
        return new com.cloud.gateway.api.facade.admin.definition.EndpointsApiView.Endpoint(
                endpoint.endpointId().value(), endpoint.serviceId().value(), endpoint.routeId().value(),
                endpoint.matchers().stream()
                        .map(matcher -> new com.cloud.gateway.api.facade.admin.definition.EndpointsApiView.Matcher(
                                matcher.httpMethod(), matcher.pathPattern()))
                        .collect(Collectors.toList()),
                endpoint.tags(), status(endpoint.status()), requirement(endpoint.access().clientVerificationRequirement()),
                requirement(endpoint.access().subjectAuthenticationRequirement()),
                endpoint.access().requiredScopes(), endpoint.access().requiredAudiences());
    }

    static com.cloud.gateway.api.facade.admin.definition.AppsApiView.App appView(App app) {
        return new com.cloud.gateway.api.facade.admin.definition.AppsApiView.App(
                app.appId().value(), app.platform(), status(app.status()), app.access().allowedOrigins());
    }

    static com.cloud.gateway.api.facade.admin.definition.ClientsApiView.Client clientView(Client client) {
        var credentials = client.access().credentials();
        return new com.cloud.gateway.api.facade.admin.definition.ClientsApiView.Client(
                client.clientId().value(), status(client.status()),
                appIdValues(client.access().allowedApps()),
                credentials == null ? null : credentialView(credentials.activeCredential()),
                credentials == null ? null
                        : credentials.graceCredentialOptional().map(AccessDefinitionApiMapper::credentialView)
                                .orElse(null));
    }
}

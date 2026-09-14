package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.infrastructure.policy.mapstruct.PolicyConfigStorageMapStructMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class StoredAccessSnapshotMapper {
    private final PolicyConfigStorageMapper policies = org.mapstruct.factory.Mappers.getMapper(
            PolicyConfigStorageMapStructMapper.class);

    public StoredPolicyConfig toStoredPolicies(PolicyConfig source) {
        return policies.toStored(source);
    }

    public PolicyConfig toDomainPolicies(StoredPolicyConfig source) {
        return policies.toDomain(source);
    }

    public StoredAccessSnapshot toStore(IngressAccessSnapshot source) {
        IngressAccessContent content = source.content();
        return new StoredAccessSnapshot(
                source.version(),
                toStoredEndpoints(content.endpointRegistry()),
                toStoredApps(content.appPolicies()),
                toStoredClients(content.clients()),
                toStoredEndpointAccess(content.endpointPolicies()),
                toStoredPolicies(content.policies()),
                source.contentHash(),
                source.publishedAt());
    }

    public IngressAccessSnapshot toDomain(StoredAccessSnapshot source) {
        return new IngressAccessSnapshot(source.snapshotVersion(), toDomainContent(source),
                source.contentHash(), source.publishedAt());
    }

    public IngressAccessContent toDomainContent(StoredAccessSnapshot source) {
        return new IngressAccessContent(toDomainEndpointRegistry(source.endpoints()),
                toDomainApps(source.apps()), toDomainClients(source.clients()),
                toDomainEndpointAccess(source.routes()), toDomainPolicies(source.policies()));
    }

    public List<StoredAccessSnapshot.Endpoint> toStoredEndpoints(EndpointRegistry source) {
        EndpointPathPatternValidator.validate(source);
        return source.definitions().stream()
                .sorted(Comparator.comparing(value -> value.endpointId().value()))
                .map(this::endpointDocument)
                .toList();
    }

    public Map<String, StoredAccessSnapshot.App> toStoredApps(
            Map<AppId, AppAccess> source
    ) {
        return source.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().value(),
                entry -> new StoredAccessSnapshot.App(
                        entry.getValue().status(),
                        entry.getValue().platform(),
                        entry.getValue().allowedOrigins()),
                (left, right) -> left,
                TreeMap::new));
    }

    public Map<String, StoredAccessSnapshot.Client> toStoredClients(
            Map<ClientId, ClientAccess> source
    ) {
        return source.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().value(),
                entry -> new StoredAccessSnapshot.Client(entry.getValue().allowedApps().stream()
                        .map(AppId::value).collect(Collectors.toUnmodifiableSet()), entry.getValue().status(),
                        credentialsDocument(entry.getValue().credentials())),
                (left, right) -> left,
                TreeMap::new));
    }

    private StoredAccessSnapshot.ClientCredentials credentialsDocument(Credentials source) {
        return source == null ? null : new StoredAccessSnapshot.ClientCredentials(
                credentialDocument(source.activeCredential()),
                source.graceCredential() == null ? null : credentialDocument(source.graceCredential()));
    }

    public Map<String, StoredAccessSnapshot.Route> toStoredEndpointAccess(
            Map<EndpointId, EndpointAccess> source
    ) {
        return source.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().value(),
                entry -> endpointAccessDocument(entry.getValue()),
                (left, right) -> left,
                TreeMap::new));
    }

    public EndpointRegistry toDomainEndpointRegistry(List<StoredAccessSnapshot.Endpoint> source) {
        EndpointRegistry registry = new EndpointRegistry(source.stream().map(this::endpointDomain).toList());
        EndpointPathPatternValidator.validate(registry);
        return registry;
    }

    public Map<AppId, AppAccess> toDomainApps(
            Map<String, StoredAccessSnapshot.App> source
    ) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new AppId(entry.getKey()),
                        entry -> new AppAccess(new AppId(entry.getKey()),
                                entry.getValue().platform(),
                                entry.getValue().allowedOrigins(),
                                entry.getValue().status())));
    }

    public Map<ClientId, ClientAccess> toDomainClients(
            Map<String, StoredAccessSnapshot.Client> source
    ) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new ClientId(entry.getKey()),
                        entry -> new ClientAccess(new ClientId(entry.getKey()), entry.getValue().allowedAppIds().stream()
                                        .map(AppId::new).collect(Collectors.toUnmodifiableSet()), credentialsDomain(entry.getKey(), entry.getValue().credentials()), entry.getValue().status())));
    }

    private Credentials credentialsDomain(String clientId, StoredAccessSnapshot.ClientCredentials source) {
        return source == null ? null : new Credentials(
                credentialDomain(clientId, source.activeCredential()),
                source.graceCredential() == null ? null : credentialDomain(clientId, source.graceCredential()));
    }

    public Map<EndpointId, EndpointAccess> toDomainEndpointAccess(
            Map<String, StoredAccessSnapshot.Route> source
    ) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new EndpointId(entry.getKey()),
                        entry -> endpointAccessDomain(entry.getKey(), entry.getValue())));
    }

    private StoredAccessSnapshot.Endpoint endpointDocument(Endpoint source) {
        return new StoredAccessSnapshot.Endpoint(source.endpointId().value(), source.serviceId(),
                source.matchers().stream().map(matcher -> new StoredAccessSnapshot.Matcher(
                        matcher.httpMethod(), matcher.pathPattern())).toList(), source.routeId(), source.tags());
    }

    private Endpoint endpointDomain(StoredAccessSnapshot.Endpoint source) {
        return new Endpoint(new EndpointId(source.endpointId()), source.serviceId(),
                source.matchers().stream().map(matcher -> new EndpointMatcher(
                        matcher.httpMethod(), matcher.pathPattern())).toList(), source.routeId(), source.tags());
    }

    private StoredAccessSnapshot.Credential credentialDocument(Credential source) {
        return new StoredAccessSnapshot.Credential(source.credentialId().value(), source.validFrom(),
                source.expiresAt(), source.signatureContract(), source.secretRef());
    }

    private Credential credentialDomain(
            String clientId, StoredAccessSnapshot.Credential source) {
        return new Credential(new CredentialId(source.credentialId()), new ClientId(clientId),
                source.signatureContract(), source.secretRef(),
                source.validFrom(), source.expiresAt());
    }

    private StoredAccessSnapshot.Route endpointAccessDocument(EndpointAccess source) {
        CredentialForwarding forwarding = source.credentialForwarding();
        return new StoredAccessSnapshot.Route(source.clientVerificationMode(),
                source.clientVerificationRequirement(), source.subjectAuthenticationRequirement(), source.requiredScopes(),
                source.requiredAudiences(), new StoredAccessSnapshot.Forwarding(
                        forwarding.authorizationHeader(), forwarding.cookies(), forwarding.allowedCookieNames()),
                source.status());
    }

    private EndpointAccess endpointAccessDomain(String endpointId, StoredAccessSnapshot.Route source) {
        StoredAccessSnapshot.Forwarding forwarding = source.credentialForwarding();
        return new EndpointAccess(new EndpointId(endpointId), source.clientVerificationRequirement(), source.clientVerificationMode(), source.subjectAuthencationRequirement(), source.requiredScopes(), source.requiredAudiences(), new CredentialForwarding(forwarding.authorizationHeader(),
                        forwarding.cookies(), forwarding.allowedCookieNames()), source.status());
    }
}

package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.policy.bundle.PolicyComposition;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.infrastructure.policy.mapstruct.PolicyCompositionStorageMapStructMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class StoredPolicySnapshotMapper {
    private final PolicyCompositionStorageMapper policies = org.mapstruct.factory.Mappers.getMapper(
            PolicyCompositionStorageMapStructMapper.class);

    public StoredPolicyComposition toStoredPolicies(PolicyComposition source) {
        return policies.toStored(source);
    }

    public PolicyComposition toDomainPolicies(StoredPolicyComposition source) {
        return policies.toDomain(source);
    }

    public StoredPolicySnapshot toStore(IngressAccessSnapshot source) {
        IngressAccessContent content = source.content();
        return new StoredPolicySnapshot(
                source.version(),
                toStoredEndpoints(content.endpointRegistry()),
                toStoredApps(content.appPolicies()),
                toStoredClients(content.clients()),
                toStoredEndpointAccess(content.endpointPolicies()),
                toStoredPolicies(content.policies()),
                source.contentHash(),
                source.publishedAt());
    }

    public IngressAccessSnapshot toDomain(StoredPolicySnapshot source) {
        return new IngressAccessSnapshot(source.snapshotVersion(), toDomainContent(source),
                source.contentHash(), source.publishedAt());
    }

    public IngressAccessContent toDomainContent(StoredPolicySnapshot source) {
        return new IngressAccessContent(toDomainEndpointRegistry(source.endpoints()),
                toDomainApps(source.apps()), toDomainClients(source.clients()),
                toDomainEndpointAccess(source.routes()), toDomainPolicies(source.policies()));
    }

    public List<StoredPolicySnapshot.Endpoint> toStoredEndpoints(EndpointRegistry source) {
        EndpointPathPatternValidator.validate(source);
        return source.definitions().stream()
                .sorted(Comparator.comparing(value -> value.endpointId().value()))
                .map(this::endpointDocument)
                .toList();
    }

    public Map<String, StoredPolicySnapshot.App> toStoredApps(
            Map<AppId, AppPolicy> source
    ) {
        return source.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().value(),
                entry -> new StoredPolicySnapshot.App(
                        entry.getValue().status(),
                        entry.getValue().platform(),
                        entry.getValue().allowedOrigins()),
                (left, right) -> left,
                TreeMap::new));
    }

    public Map<String, StoredPolicySnapshot.Client> toStoredClients(
            Map<ClientId, ClientPolicy> source
    ) {
        return source.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().value(),
                entry -> new StoredPolicySnapshot.Client(entry.getValue().allowedApps().stream()
                        .map(AppId::value).collect(Collectors.toUnmodifiableSet()), entry.getValue().status(),
                        credentialsDocument(entry.getValue().credentials())),
                (left, right) -> left,
                TreeMap::new));
    }

    private StoredPolicySnapshot.ClientCredentials credentialsDocument(Credentials source) {
        return source == null ? null : new StoredPolicySnapshot.ClientCredentials(
                credentialDocument(source.activeCredential()),
                source.graceCredential() == null ? null : credentialDocument(source.graceCredential()));
    }

    public Map<String, StoredPolicySnapshot.Route> toStoredEndpointAccess(
            Map<EndpointId, EndpointPolicy> source
    ) {
        return source.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().value(),
                entry -> endpointAccessDocument(entry.getValue()),
                (left, right) -> left,
                TreeMap::new));
    }

    public EndpointRegistry toDomainEndpointRegistry(List<StoredPolicySnapshot.Endpoint> source) {
        EndpointRegistry registry = new EndpointRegistry(source.stream().map(this::endpointDomain).toList());
        EndpointPathPatternValidator.validate(registry);
        return registry;
    }

    public Map<AppId, AppPolicy> toDomainApps(
            Map<String, StoredPolicySnapshot.App> source
    ) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new AppId(entry.getKey()),
                        entry -> new AppPolicy(new AppId(entry.getKey()),
                                entry.getValue().platform(),
                                entry.getValue().allowedOrigins(),
                                entry.getValue().status())));
    }

    public Map<ClientId, ClientPolicy> toDomainClients(
            Map<String, StoredPolicySnapshot.Client> source
    ) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new ClientId(entry.getKey()),
                        entry -> new ClientPolicy(new ClientId(entry.getKey()), entry.getValue().allowedAppIds().stream()
                                        .map(AppId::new).collect(Collectors.toUnmodifiableSet()), credentialsDomain(entry.getKey(), entry.getValue().credentials()), entry.getValue().status())));
    }

    private Credentials credentialsDomain(String clientId, StoredPolicySnapshot.ClientCredentials source) {
        return source == null ? null : new Credentials(
                credentialDomain(clientId, source.activeCredential()),
                source.graceCredential() == null ? null : credentialDomain(clientId, source.graceCredential()));
    }

    public Map<EndpointId, EndpointPolicy> toDomainEndpointAccess(
            Map<String, StoredPolicySnapshot.Route> source
    ) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new EndpointId(entry.getKey()),
                        entry -> endpointAccessDomain(entry.getKey(), entry.getValue())));
    }

    private StoredPolicySnapshot.Endpoint endpointDocument(Endpoint source) {
        return new StoredPolicySnapshot.Endpoint(source.endpointId().value(), source.serviceId(),
                source.matchers().stream().map(matcher -> new StoredPolicySnapshot.Matcher(
                        matcher.httpMethod(), matcher.pathPattern())).toList(), source.routeId(), source.tags());
    }

    private Endpoint endpointDomain(StoredPolicySnapshot.Endpoint source) {
        return new Endpoint(new EndpointId(source.endpointId()), source.serviceId(),
                source.matchers().stream().map(matcher -> new EndpointMatcher(
                        matcher.httpMethod(), matcher.pathPattern())).toList(), source.routeId(), source.tags());
    }

    private StoredPolicySnapshot.Credential credentialDocument(Credential source) {
        return new StoredPolicySnapshot.Credential(source.credentialId().value(), source.validFrom(),
                source.expiresAt(), source.signatureContract(), source.secretRef());
    }

    private Credential credentialDomain(
            String clientId, StoredPolicySnapshot.Credential source) {
        return new Credential(new CredentialId(source.credentialId()), new ClientId(clientId),
                source.signatureContract(), source.secretRef(),
                source.validFrom(), source.expiresAt());
    }

    private StoredPolicySnapshot.Route endpointAccessDocument(EndpointPolicy source) {
        CredentialForwarding forwarding = source.credentialForwarding();
        return new StoredPolicySnapshot.Route(source.clientVerificationMode(),
                source.clientVerificationRequirement(), source.subjectAuthencationRequirement(), source.requiredScopes(),
                source.requiredAudiences(), new StoredPolicySnapshot.Forwarding(
                        forwarding.authorizationHeader(), forwarding.cookies(), forwarding.allowedCookieNames()),
                source.status());
    }

    private EndpointPolicy endpointAccessDomain(String endpointId, StoredPolicySnapshot.Route source) {
        StoredPolicySnapshot.Forwarding forwarding = source.credentialForwarding();
        return new EndpointPolicy(new EndpointId(endpointId), source.clientVerificationRequirement(), source.clientVerificationMode(), source.subjectAuthencationRequirement(), source.requiredScopes(), source.requiredAudiences(), new CredentialForwarding(forwarding.authorizationHeader(),
                        forwarding.cookies(), forwarding.allowedCookieNames()), source.status());
    }
}

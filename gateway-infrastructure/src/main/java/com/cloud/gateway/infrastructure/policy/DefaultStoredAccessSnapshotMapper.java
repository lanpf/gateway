package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public final class DefaultStoredAccessSnapshotMapper implements StoredAccessSnapshotMapper {
    private final CapabilityCompositionStorageMapper policies;
    private final AccessSnapshotDocumentMapper documents;

    @Override
    public StoredCapabilityComposition toStoredPolicies(CapabilityComposition source) {
        return policies.toStored(source);
    }

    @Override
    public CapabilityComposition toDomainPolicies(StoredCapabilityComposition source) {
        return policies.toDomain(source);
    }

    @Override
    public StoredAccessSnapshot toStore(IngressSnapshot source) {
        IngressContent content = source.content();
        return documents.snapshot(source, toStoredEndpoints(content.endpoints()),
                toStoredApps(content.apps()), toStoredClients(content.clients()),
                content.capabilities());
    }

    @Override
    public IngressSnapshot toDomain(StoredAccessSnapshot source) {
        return new IngressSnapshot(new SnapshotVersion(source.snapshotVersion()),
                toDomainContent(source), source.contentHash(), source.publishedAt());
    }

    @Override
    public IngressContent toDomainContent(StoredAccessSnapshot source) {
        return new IngressContent(toDomainApps(source.apps()), toDomainClients(source.clients()),
                toDomainEndpointRegistry(source.endpoints()), toDomainPolicies(source.policies()));
    }

    @Override
    public List<StoredAccessSnapshot.Endpoint> toStoredEndpoints(EndpointRegistry source) {
        EndpointPathPatternValidator.validate(source);
        return source.values().stream()
                .sorted(Comparator.comparing(value -> value.endpointId().value()))
                .map(documents::endpoint)
                .toList();
    }

    @Override
    public Map<String, StoredAccessSnapshot.App> toStoredApps(AppRegistry source) {
        return source.values().stream().collect(Collectors.toMap(
                app -> app.appId().value(),
                documents::app,
                (left, right) -> left,
                TreeMap::new));
    }

    @Override
    public Map<String, StoredAccessSnapshot.Client> toStoredClients(ClientRegistry source) {
        return source.values().stream().collect(Collectors.toMap(
                client -> client.clientId().value(),
                documents::client,
                (left, right) -> left,
                TreeMap::new));
    }

    @Override
    public EndpointRegistry toDomainEndpointRegistry(List<StoredAccessSnapshot.Endpoint> source) {
        EndpointRegistry registry = new EndpointRegistry(source.stream().map(this::endpointDomain).toList());
        EndpointPathPatternValidator.validate(registry);
        return registry;
    }

    @Override
    public AppRegistry toDomainApps(Map<String, StoredAccessSnapshot.App> source) {
        return new AppRegistry(source.entrySet().stream()
                .map(entry -> new App(new AppId(entry.getKey()), entry.getValue().platform(),
                        entry.getValue().status(), new AppAccess(entry.getValue().allowedOrigins())))
                .toList());
    }

    @Override
    public ClientRegistry toDomainClients(Map<String, StoredAccessSnapshot.Client> source) {
        return new ClientRegistry(source.entrySet().stream()
                .map(entry -> new Client(new ClientId(entry.getKey()), entry.getValue().status(),
                        new ClientAccess(entry.getValue().allowedAppIds().stream().map(AppId::new)
                                .collect(Collectors.toUnmodifiableSet()),
                                credentialsDomain(entry.getValue().credentials()))))
                .toList());
    }

    private Credentials credentialsDomain(StoredAccessSnapshot.ClientCredentials source) {
        return source == null ? null : new Credentials(
                credentialDomain(source.activeCredential()),
                source.graceCredential() == null ? null : credentialDomain(source.graceCredential()));
    }

    private Endpoint endpointDomain(StoredAccessSnapshot.Endpoint source) {
        return new Endpoint(new EndpointId(source.endpointId()), new ServiceId(source.serviceId()),
                new RouteId(source.routeId()),
                source.matchers().stream().map(matcher -> new EndpointMatcher(
                        matcher.httpMethod(), matcher.pathPattern())).toList(),
                source.tags(), source.status(), accessDomain(source.access()));
    }

    private EndpointAccess accessDomain(StoredAccessSnapshot.Access source) {
        return new EndpointAccess(source.clientVerificationRequirement(),
                source.subjectAuthenticationRequirement(), source.requiredScopes(),
                source.requiredAudiences());
    }

    private Credential credentialDomain(StoredAccessSnapshot.Credential source) {
        return new Credential(new CredentialId(source.credentialId()), source.secretRef(),
                source.signatureContract(), source.validFrom(), source.expiresAt());
    }
}

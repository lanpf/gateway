package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.DefaultCanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
import com.cloud.gateway.domain.policy.header.HeaderRules;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;

final class PolicyTestFixtures {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-26T00:00:00Z");

    private PolicyTestFixtures() { }

    static com.cloud.gateway.application.configuration.publication.AccessSnapshotHasher hasher() {
        return new CanonicalAccessSnapshotHasher(new DefaultCanonicalPolicyContentMapper(new CapabilityCompositionStorageMapStructMapperImpl()), new ObjectMapper());
    }

    static StoredAccessSnapshotCodec codec() {
        return new StoredAccessSnapshotCodec(
                new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl())), hasher(), new ObjectMapper().findAndRegisterModules(),
                HeaderRules.registeredOnly());
    }

    static IngressSnapshot snapshot(long version, boolean enabled) {
        EndpointId endpointId = new EndpointId("USER_AUTH.LOGIN");
        EndpointRegistry endpoints = new EndpointRegistry(List.of(new Endpoint(endpointId,
                new ServiceId("user-auth"), new RouteId("user-auth"),
                List.of(new EndpointMatcher("POST", "/api/user-auth/login")), Set.of(),
                enabled ? EndpointStatus.ENABLED : EndpointStatus.DISABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()))));
        IngressContent content = new IngressContent(new AppRegistry(List.of()),
                new ClientRegistry(List.of()), endpoints, CapabilityComposition.empty());
        return new IngressSnapshot(new SnapshotVersion(version), content,
                hasher().hash(content), PUBLISHED_AT.plusSeconds(version));
    }
}

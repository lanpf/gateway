package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.DefaultCanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityPolicyConfigStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractAccessSnapshotDocumentMapStructMapperImpl;

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
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;

final class PolicyTestFixtures {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-26T00:00:00Z");

    private PolicyTestFixtures() { }

    static com.cloud.gateway.application.configuration.publication.AccessSnapshotHasher hasher() {
        return new CanonicalAccessSnapshotHasher(new DefaultCanonicalPolicyContentMapper(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))), new ObjectMapper());
    }

    static StoredAccessSnapshotCodec codec() {
        return new StoredAccessSnapshotCodec(new DefaultStoredAccessSnapshotMapper(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AbstractAccessSnapshotDocumentMapStructMapperImpl(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())))), hasher(), new ObjectMapper().findAndRegisterModules(), BuiltInCapabilityCatalog.validator(HeaderConfigRule.empty()));
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
                new ClientRegistry(List.of()), endpoints, CapabilityPolicyConfig.empty());
        return new IngressSnapshot(new SnapshotVersion(version), content,
                hasher().hash(content), PUBLISHED_AT.plusSeconds(version));
    }
}

package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.access.publication.AccessSnapshotHasher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PolicyTestFixtures {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-26T00:00:00Z");

    private PolicyTestFixtures() { }

    static com.cloud.gateway.application.access.publication.AccessSnapshotHasher hasher() {
        return new CanonicalAccessSnapshotHasher(new CanonicalPolicyContentMapper(), new ObjectMapper());
    }

    static StoredAccessSnapshotCodec codec() {
        return new StoredAccessSnapshotCodec(
                new StoredAccessSnapshotMapper(), hasher(), new ObjectMapper().findAndRegisterModules(),
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
    }

    static IngressAccessSnapshot snapshot(long version, boolean enabled) {
        EndpointId endpointId = new EndpointId("USER_AUTH.LOGIN");
        EndpointRegistry catalog = new EndpointRegistry(List.of(new Endpoint(endpointId, "user-auth",
                List.of(new EndpointMatcher("POST", "/api/user-auth/login")), "user-auth")));
        EndpointAccess route = new EndpointAccess(endpointId, ClientVerificationRequirement.FORBIDDEN, ClientVerificationMode.IGNORE, SubjectAuthenticationRequirement.IGNORE, Set.of(), Set.of(), CredentialForwarding.stripAll(), enabled ? EndpointStatus.ENABLED : EndpointStatus.DISABLED);
        IngressAccessContent content = new IngressAccessContent(
                catalog, Map.of(), Map.of(), Map.of(endpointId, route));
        return new IngressAccessSnapshot(version, content, hasher().hash(content), PUBLISHED_AT.plusSeconds(version));
    }
}

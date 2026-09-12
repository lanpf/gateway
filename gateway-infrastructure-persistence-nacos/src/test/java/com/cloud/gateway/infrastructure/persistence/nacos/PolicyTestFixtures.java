package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicySnapshotHasher;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PolicyTestFixtures {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-26T00:00:00Z");

    private PolicyTestFixtures() { }

    static PolicySnapshotHasher hasher() {
        return new CanonicalPolicySnapshotHasher(new CanonicalPolicyContentMapper(), new ObjectMapper());
    }

    static StoredPolicySnapshotCodec codec() {
        return new StoredPolicySnapshotCodec(
                new StoredPolicySnapshotMapper(), hasher(), new ObjectMapper().findAndRegisterModules(),
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
    }

    static IngressAccessSnapshot snapshot(long version, boolean enabled) {
        EndpointId endpointId = new EndpointId("USER_AUTH.LOGIN");
        EndpointRegistry catalog = new EndpointRegistry(List.of(new Endpoint(endpointId, "user-auth",
                List.of(new EndpointMatcher("POST", "/api/user-auth/login")), "user-auth")));
        EndpointPolicy route = new EndpointPolicy(endpointId, ClientVerificationRequirement.FORBIDDEN, ClientVerificationMode.IGNORE, SubjectAuthencationRequirement.IGNORE, Set.of(), Set.of(), CredentialForwarding.stripAll(), enabled ? EndpointStatus.ENABLED : EndpointStatus.DISABLED);
        IngressAccessContent content = new IngressAccessContent(
                catalog, Map.of(), Map.of(), Map.of(endpointId, route));
        return new IngressAccessSnapshot(version, content, hasher().hash(content), PUBLISHED_AT.plusSeconds(version));
    }
}

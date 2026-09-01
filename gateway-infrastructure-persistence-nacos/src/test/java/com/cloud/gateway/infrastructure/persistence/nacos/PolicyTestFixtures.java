package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.CredentialForwardingPolicy;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.SubjectRequirement;
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
                new StoredPolicySnapshotMapper(), hasher(), new ObjectMapper().findAndRegisterModules());
    }

    static IngressPolicySnapshot snapshot(long version, boolean enabled) {
        EndpointId endpointId = new EndpointId("USER_AUTH.LOGIN");
        EndpointCatalog catalog = new EndpointCatalog(List.of(new EndpointDefinition(endpointId, "user-auth",
                List.of(new EndpointMatcher("POST", "/api/user-auth/login")), "user-auth")));
        IngressRouteAccessPolicy route = new IngressRouteAccessPolicy(endpointId,
                ChannelCredentialRequirement.IGNORE, ChannelRequirement.FORBIDDEN, SubjectRequirement.IGNORE,
                Set.of(), Set.of(), CredentialForwardingPolicy.stripAll(), enabled);
        IngressPolicyContent content = new IngressPolicyContent(
                catalog, Map.of(), Map.of(), Map.of(), Map.of(endpointId, route));
        return new IngressPolicySnapshot(version, content, hasher().hash(content), PUBLISHED_AT.plusSeconds(version));
    }
}

package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.policy.bundle.PolicyBundle;
import com.cloud.gateway.domain.policy.bundle.PolicyBundleId;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import com.cloud.gateway.domain.policy.header.HeaderRules;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaderAdmissionCodecTest {
    @Test void shouldValidateDefinitionsAndSnapshotsAgainstDeploymentRules() {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        var mapper = new StoredAccessSnapshotMapper();
        var allowed = new HeaderRules(Set.of("X-Deployment"), Set.of(), Set.of());
        HeaderRules denied = HeaderRules.registeredOnly();
        var id = new PolicyBundleId("headers");
        var policy = new HeaderPolicy(Set.of(), Map.of("X-Deployment", "prod"), Set.of(), null);
        var configuration = new PolicyConfig(Map.of(id,
                new PolicyBundle(id, null, null, null, null, policy)), List.of());
        var allowedDefinitions = new StoredPolicyDefinitionCodec(mapper, json, allowed);
        var deniedDefinitions = new StoredPolicyDefinitionCodec(mapper, json, denied);
        String definition = allowedDefinitions.encodePolicyConfiguration(configuration);
        assertEquals(configuration, allowedDefinitions.decodePolicyConfiguration(definition));
        assertThrows(DomainException.class, () -> deniedDefinitions.encodePolicyConfiguration(configuration));
        assertThrows(InfrastructureException.class, () -> deniedDefinitions.decodePolicyConfiguration(definition));

        var hasher = new CanonicalAccessSnapshotHasher(new CanonicalPolicyContentMapper(), json);
        var content = new IngressAccessContent(new EndpointRegistry(List.of()), Map.of(), Map.of(), Map.of(), configuration);
        var snapshot = new IngressAccessSnapshot(1, content, hasher.hash(content), Instant.EPOCH);
        var allowedSnapshots = new StoredAccessSnapshotCodec(mapper, hasher, json, allowed);
        var deniedSnapshots = new StoredAccessSnapshotCodec(mapper, hasher, json, denied);
        String encoded = allowedSnapshots.encodeSnapshot(snapshot);
        assertEquals(configuration, allowedSnapshots.decodeSnapshot(encoded).content().policies());
        assertThrows(DomainException.class, () -> deniedSnapshots.encodeSnapshot(snapshot));
        assertThrows(InfrastructureException.class, () -> deniedSnapshots.decodeSnapshot(encoded));
    }
}

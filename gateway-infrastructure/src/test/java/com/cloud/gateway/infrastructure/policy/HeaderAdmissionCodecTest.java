package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.DefaultCanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.policy.bundle.CapabilityBundle;
import com.cloud.gateway.domain.policy.bundle.CapabilityBundleId;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
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
        var mapper = new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl()));
        var allowed = new HeaderRules(Set.of("X-Deployment"), Set.of(), Set.of());
        HeaderRules denied = HeaderRules.registeredOnly();
        var id = new CapabilityBundleId("headers");
        var policy = new HeaderPolicy(Set.of(), Map.of("X-Deployment", "prod"), Set.of(), null);
        var configuration = new CapabilityComposition(Map.of(id,
                new CapabilityBundle(id, null, null, null, null, policy)), List.of());
        var allowedDefinitions = new StoredPolicyDefinitionCodec(mapper, json, allowed);
        var deniedDefinitions = new StoredPolicyDefinitionCodec(mapper, json, denied);
        String definition = allowedDefinitions.encodeCapabilityComposition(configuration);
        assertEquals(configuration, allowedDefinitions.decodeCapabilityComposition(definition));
        assertThrows(DomainException.class, () -> deniedDefinitions.encodeCapabilityComposition(configuration));
        assertThrows(InfrastructureException.class, () -> deniedDefinitions.decodeCapabilityComposition(definition));

        var hasher = new CanonicalAccessSnapshotHasher(new DefaultCanonicalPolicyContentMapper(new CapabilityCompositionStorageMapStructMapperImpl()), json);
        var content = new IngressContent(new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of()), configuration);
        var snapshot = new IngressSnapshot(new SnapshotVersion(1), content,
                hasher.hash(content), Instant.EPOCH);
        var allowedSnapshots = new StoredAccessSnapshotCodec(mapper, hasher, json, allowed);
        var deniedSnapshots = new StoredAccessSnapshotCodec(mapper, hasher, json, denied);
        String encoded = allowedSnapshots.encodeSnapshot(snapshot);
        assertEquals(configuration, allowedSnapshots.decodeSnapshot(encoded).content().capabilityComposition());
        assertThrows(DomainException.class, () -> deniedSnapshots.encodeSnapshot(snapshot));
        assertThrows(InfrastructureException.class, () -> deniedSnapshots.decodeSnapshot(encoded));
    }
}

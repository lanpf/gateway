package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityPolicyConfigStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractAccessSnapshotDocumentMapStructMapperImpl;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.capability.policy.CapabilityBundle;
import com.cloud.gateway.domain.capability.policy.CapabilityBundleId;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
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
        var allowed = HeaderConfigRule.empty();
        var mapper = new DefaultStoredAccessSnapshotMapper(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(allowed), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AbstractAccessSnapshotDocumentMapStructMapperImpl(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(allowed), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))));
        HeaderConfigRule denied = new HeaderConfigRule(Set.of("X-Deployment"), Set.of());
        var id = new CapabilityBundleId("header");
        var policy = new HeaderConfig(Set.of(), Map.of("X-Deployment", "prod"), Set.of(), null);
        var configuration = new CapabilityPolicyConfig(java.util.List.of(new CapabilityBundle(id, java.util.List.of(policy))), List.of());
        var allowedDefinitions = new StoredPolicyDefinitionCodec(mapper, json, BuiltInCapabilityCatalog.validator(allowed));
        var deniedDefinitions = new StoredPolicyDefinitionCodec(mapper, json, BuiltInCapabilityCatalog.validator(denied));
        String definition = allowedDefinitions.encodeCapabilityPolicyConfig(configuration);
        assertEquals(configuration, allowedDefinitions.decodeCapabilityPolicyConfig(definition));
        assertThrows(DomainException.class, () -> deniedDefinitions.encodeCapabilityPolicyConfig(configuration));
        assertThrows(InfrastructureException.class, () -> deniedDefinitions.decodeCapabilityPolicyConfig(definition));

        var hasher = new CanonicalAccessSnapshotHasher(new DefaultCanonicalPolicyContentMapper(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(allowed), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))), json);
        var content = new IngressContent(new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of()), configuration);
        var snapshot = new IngressSnapshot(new SnapshotVersion(1), content,
                hasher.hash(content), Instant.EPOCH);
        var allowedSnapshots = new StoredAccessSnapshotCodec(mapper, hasher, json, BuiltInCapabilityCatalog.validator(allowed));
        var deniedSnapshots = new StoredAccessSnapshotCodec(mapper, hasher, json, BuiltInCapabilityCatalog.validator(denied));
        String encoded = allowedSnapshots.encodeSnapshot(snapshot);
        assertEquals(configuration, allowedSnapshots.decodeSnapshot(encoded).content().policyConfig());
        assertThrows(DomainException.class, () -> deniedSnapshots.encodeSnapshot(snapshot));
        assertThrows(InfrastructureException.class, () -> deniedSnapshots.decodeSnapshot(encoded));
    }
}

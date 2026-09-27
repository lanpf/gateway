package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;
import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AppPropertiesMapStructMapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionPropertiesMapStructMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PropertiesPolicyDefinitionRepositoriesTest {
    @Test void shouldReadCapabilityCompositionAndChangeItsRevisionWithoutOtherDefinitions() {
        var properties = new GatewayPolicyDefinitionProperties();
        var bundle = new com.cloud.gateway.infrastructure.policy.config.CapabilityCompositionProperties.Bundle();
        bundle.setBundleId("standard");
        java.util.Map<String, Object> timeout = new java.util.HashMap<>(java.util.Map.of("connect", "PT1S", "read", "PT2S", "total", "PT3S"));
        bundle.getCapabilities().put("timeout", timeout);
        properties.getPolicies().getBundles().put("standard", bundle);
        var mapper = new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))));
        CapabilityCompositionPropertiesMapStructMapper policyConfiguration = new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionPropertiesMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()));
        var codec = new StoredPolicyDefinitionCodec(mapper, new ObjectMapper().findAndRegisterModules(),
                HeaderAdmissionRules.registeredOnly());
        var repository = new PropertiesCapabilityCompositionDefinitionRepository(properties, mapper, policyConfiguration, codec);
        RevisionedAccessDefinition<CapabilityComposition> first = repository.load();
        timeout.put("total", "PT4S");
        RevisionedAccessDefinition<CapabilityComposition> second = repository.load();
        org.junit.jupiter.api.Assertions.assertNotEquals(first.sourceRevision(), second.sourceRevision());
        assertEquals(java.time.Duration.ofSeconds(4), second.value().bundles()
                .get(new com.cloud.gateway.domain.capability.configuration.CapabilityBundleId("standard")).find(com.cloud.gateway.domain.capability.resilience.TimeoutPolicyComposer.INSTANCE).orElseThrow().total());
        assertEquals(java.time.Duration.ofSeconds(3), first.value().bundles()
                .get(new com.cloud.gateway.domain.capability.configuration.CapabilityBundleId("standard")).find(com.cloud.gateway.domain.capability.resilience.TimeoutPolicyComposer.INSTANCE).orElseThrow().total());
    }

    @Test
    void shouldLoadAppsWithoutConvertingUnrelatedDefinitions() {
        GatewayPolicyDefinitionProperties properties = new GatewayPolicyDefinitionProperties();
        GatewayPolicyDefinitionProperties.App app = new GatewayPolicyDefinitionProperties.App();
        app.setStatus(AppStatus.ENABLED);
        app.setPlatform("WEB");
        app.getAllowedOrigins().add("https://browser.example.com");
        properties.getApps().put("web-app", app);
        properties.getClients().put(
                "unrelated-invalid-client", new GatewayPolicyDefinitionProperties.Client());
        StoredAccessSnapshotMapper mapper = new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))));
        StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
                mapper, new ObjectMapper().findAndRegisterModules(),
                HeaderAdmissionRules.registeredOnly());
        PropertiesAppAccessDefinitionRepository repository =
                new PropertiesAppAccessDefinitionRepository(properties, mapper, new AppPropertiesMapStructMapperImpl(), codec);

        RevisionedAccessDefinition<AppRegistry> observed = repository.load();

        assertEquals("WEB", observed.value().find(new AppId("web-app")).orElseThrow().platform());
        assertEquals(Set.of("https://browser.example.com"),
                observed.value().find(new AppId("web-app")).orElseThrow().access().allowedOrigins());
    }
}

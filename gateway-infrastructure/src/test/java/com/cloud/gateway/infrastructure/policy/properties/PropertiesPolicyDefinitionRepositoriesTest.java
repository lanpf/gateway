package com.cloud.gateway.infrastructure.policy.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import com.cloud.gateway.infrastructure.policy.mapstruct.PolicyCompositionPropertiesMapStructMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PropertiesPolicyDefinitionRepositoriesTest {
    @Test void shouldReadPolicyConfigurationAndChangeItsRevisionWithoutOtherDefinitions() {
        var properties = new GatewayPolicyDefinitionProperties();
        var bundle = new com.cloud.gateway.infrastructure.policy.config.PolicyCompositionProperties.Bundle();
        bundle.setBundleId("standard");
        var timeout = new com.cloud.gateway.infrastructure.policy.config.PolicyCompositionProperties.Timeout();
        timeout.setConnect(java.time.Duration.ofSeconds(1));
        timeout.setRead(java.time.Duration.ofSeconds(2));
        timeout.setTotal(java.time.Duration.ofSeconds(3));
        bundle.setTimeout(timeout);
        properties.getPolicies().getBundles().put("standard", bundle);
        var mapper = new StoredPolicySnapshotMapper();
        PolicyCompositionPropertiesMapStructMapper policyConfiguration = org.mapstruct.factory.Mappers.getMapper(
                com.cloud.gateway.infrastructure.policy.mapstruct.PolicyCompositionPropertiesMapStructMapper.class);
        var codec = new StoredPolicyDefinitionCodec(mapper, new ObjectMapper().findAndRegisterModules(),
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
        var repository = new PropertiesPolicyCompositionDefinitionRepository(properties, mapper, policyConfiguration, codec);
        VersionedPolicyDefinition<com.cloud.gateway.domain.policy.bundle.PolicyComposition> first = repository.load();
        timeout.setTotal(java.time.Duration.ofSeconds(4));
        VersionedPolicyDefinition<com.cloud.gateway.domain.policy.bundle.PolicyComposition> second = repository.load();
        org.junit.jupiter.api.Assertions.assertNotEquals(first.sourceRevision(), second.sourceRevision());
        assertEquals(java.time.Duration.ofSeconds(4), second.value().bundles()
                .get(new com.cloud.gateway.domain.policy.bundle.PolicyBundleId("standard")).timeout().total());
        assertEquals(java.time.Duration.ofSeconds(3), first.value().bundles()
                .get(new com.cloud.gateway.domain.policy.bundle.PolicyBundleId("standard")).timeout().total());
    }

    @Test
    void shouldLoadAppsWithoutConvertingUnrelatedDefinitions() {
        GatewayPolicyDefinitionProperties properties = new GatewayPolicyDefinitionProperties();
        GatewayPolicyDefinitionProperties.App app = new GatewayPolicyDefinitionProperties.App();
        app.setStatus(PolicyStatus.ENABLED);
        app.setPlatform("WEB");
        app.getAllowedOrigins().add("https://browser.example.com");
        properties.getApps().put("web-app", app);
        properties.getClients().put(
                "unrelated-invalid-client", new GatewayPolicyDefinitionProperties.Client());
        StoredPolicySnapshotMapper mapper = new StoredPolicySnapshotMapper();
        StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
                mapper, new ObjectMapper().findAndRegisterModules(),
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
        PropertiesAppPolicyDefinitionRepository repository =
                new PropertiesAppPolicyDefinitionRepository(properties, mapper, codec);

        VersionedPolicyDefinition<Map<AppId, AppPolicy>> observed = repository.load();

        assertEquals("WEB", observed.value().get(new AppId("web-app")).platform());
        assertEquals(Set.of("https://browser.example.com"),
                observed.value().get(new AppId("web-app")).allowedOrigins());
    }
}

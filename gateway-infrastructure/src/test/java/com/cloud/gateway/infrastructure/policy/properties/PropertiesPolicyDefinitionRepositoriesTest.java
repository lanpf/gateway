package com.cloud.gateway.infrastructure.policy.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloud.gateway.application.access.definition.VersionedAccessDefinition;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import com.cloud.gateway.infrastructure.policy.mapstruct.PolicyConfigPropertiesMapStructMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PropertiesPolicyDefinitionRepositoriesTest {
    @Test void shouldReadPolicyConfigurationAndChangeItsRevisionWithoutOtherDefinitions() {
        var properties = new GatewayPolicyDefinitionProperties();
        var bundle = new com.cloud.gateway.infrastructure.policy.config.PolicyConfigProperties.Bundle();
        bundle.setBundleId("standard");
        var timeout = new com.cloud.gateway.infrastructure.policy.config.PolicyConfigProperties.Timeout();
        timeout.setConnect(java.time.Duration.ofSeconds(1));
        timeout.setRead(java.time.Duration.ofSeconds(2));
        timeout.setTotal(java.time.Duration.ofSeconds(3));
        bundle.setTimeout(timeout);
        properties.getPolicies().getBundles().put("standard", bundle);
        var mapper = new StoredAccessSnapshotMapper();
        PolicyConfigPropertiesMapStructMapper policyConfiguration = org.mapstruct.factory.Mappers.getMapper(
                com.cloud.gateway.infrastructure.policy.mapstruct.PolicyConfigPropertiesMapStructMapper.class);
        var codec = new StoredPolicyDefinitionCodec(mapper, new ObjectMapper().findAndRegisterModules(),
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
        var repository = new PropertiesPolicyConfigDefinitionRepository(properties, mapper, policyConfiguration, codec);
        com.cloud.gateway.application.access.definition.VersionedAccessDefinition<com.cloud.gateway.domain.policy.bundle.PolicyConfig> first = repository.load();
        timeout.setTotal(java.time.Duration.ofSeconds(4));
        com.cloud.gateway.application.access.definition.VersionedAccessDefinition<com.cloud.gateway.domain.policy.bundle.PolicyConfig> second = repository.load();
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
        StoredAccessSnapshotMapper mapper = new StoredAccessSnapshotMapper();
        StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
                mapper, new ObjectMapper().findAndRegisterModules(),
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
        PropertiesAppAccessDefinitionRepository repository =
                new PropertiesAppAccessDefinitionRepository(properties, mapper, codec);

        com.cloud.gateway.application.access.definition.VersionedAccessDefinition<Map<AppId, AppAccess>> observed = repository.load();

        assertEquals("WEB", observed.value().get(new AppId("web-app")).platform());
        assertEquals(Set.of("https://browser.example.com"),
                observed.value().get(new AppId("web-app")).allowedOrigins());
    }
}

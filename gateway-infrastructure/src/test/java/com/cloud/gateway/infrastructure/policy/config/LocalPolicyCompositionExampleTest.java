package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.ClientPropertiesMapStructMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.PolicyConfigPropertiesMapStructMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import static org.junit.jupiter.api.Assertions.*;

class LocalPolicyConfigExampleTest {
    @Test void shouldRejectOldMappingFieldDuringStrictLocalBinding() {
        var values = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("bundles[standard].bundle-endpointId", "standard",
                        "bundles[standard].headers.trusted-mappings[X-Gateway-App]", "APP_ID",
                        "bundles[standard].headers.trusted-headers[0]", "APP_ID"));
        var binder = new Binder(values);
        assertThrows(org.springframework.boot.context.properties.bind.BindException.class,
                () -> binder.bind("", Bindable.of(PolicyConfigProperties.class),
                        new org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler(
                                org.springframework.boot.context.properties.bind.BindHandler.DEFAULT)));
    }

    @Test void shouldRejectRemovedTrustedSourceValuesDuringLocalBinding() {
        for (String source : java.util.List.of("VERIFIED_CLIENT_ID", "SUBJECT_ID", "LEGACY_CLIENT_APP_ID")) {
            var values = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                    java.util.Map.of("bundles[standard].bundle-endpointId", "standard",
                            "bundles[standard].headers.trusted-headers[0]", source));
            var binder = new Binder(values);
            assertThrows(org.springframework.boot.context.properties.bind.BindException.class,
                    () -> binder.bind("", Bindable.of(PolicyConfigProperties.class)));
        }
    }

    @Test void shouldParseAndBindCompleteLocalExampleWithSeparateScgRoutesAndPolicyRoutes() throws Exception {
        Path file = Path.of("../config/application.yml");
        if (!java.nio.file.Files.exists(file)) {
            file = Path.of("config/application.yml");
        }
        com.fasterxml.jackson.databind.JsonNode json = new ObjectMapper(new YAMLFactory()).readTree(file.toFile());
        assertTrue(json.at("/spring/cloud/gateway/server/webflux/routes").isArray());
        java.util.List<org.springframework.core.env.PropertySource<?>> sources = new YamlPropertySourceLoader().load("local-example", new FileSystemResource(file));
        var binder = new Binder(ConfigurationPropertySources.from(sources));
        GatewayPolicyDefinitionProperties properties = binder.bind(GatewayPolicyDefinitionProperties.PREFIX,
                Bindable.of(GatewayPolicyDefinitionProperties.class)).orElseThrow(AssertionError::new);
        var mapper = new StoredAccessSnapshotMapper();
        ClientPropertiesMapStructMapper clients = org.mapstruct.factory.Mappers.getMapper(
                com.cloud.gateway.infrastructure.policy.mapstruct.ClientPropertiesMapStructMapper.class);
        PolicyConfigPropertiesMapStructMapper policyConfiguration = org.mapstruct.factory.Mappers.getMapper(
                com.cloud.gateway.infrastructure.policy.mapstruct.PolicyConfigPropertiesMapStructMapper.class);
        var content = new IngressAccessContent(mapper.toDomainEndpointRegistry(properties.toStoredEndpoints()),
                mapper.toDomainApps(properties.toStoredApps()),
                mapper.toDomainClients(clients.toStored(properties.getClients())),
                mapper.toDomainEndpointAccess(properties.toStoredRoutes()),
                mapper.toDomainPolicies(policyConfiguration.toStored(properties.getPolicies())));
        var hasher = new CanonicalAccessSnapshotHasher(new CanonicalPolicyContentMapper(), new ObjectMapper());
        assertNotNull(new IngressAccessSnapshot(1, content, hasher.hash(content), Instant.parse("2026-09-08T00:00:00Z")));
        assertEquals("gateway-admin", content.endpointRegistry().find(new EndpointId("GATEWAY.ADMIN")).orElseThrow().routeId());
        assertTrue(content.endpointPolicies().get(new EndpointId("GATEWAY.ADMIN")).requiredAudiences().contains("gateway"));
        assertEquals(1, content.policies().bundles().size());
    }
}

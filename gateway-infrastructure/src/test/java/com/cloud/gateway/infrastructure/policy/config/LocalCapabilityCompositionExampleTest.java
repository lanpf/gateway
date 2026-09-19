package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.DefaultCanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AppPropertiesMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.EndpointPropertiesMapStructMapperImpl;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.ClientPropertiesMapStructMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionPropertiesMapStructMapper;
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

class LocalCapabilityCompositionExampleTest {
    @Test void shouldRejectOldMappingFieldDuringStrictLocalBinding() {
        var values = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("bundles[standard].bundle-endpointId", "standard",
                        "bundles[standard].headers.trusted-mappings[X-Gateway-App]", "APP_ID",
                        "bundles[standard].headers.trusted-headers[0]", "APP_ID"));
        var binder = new Binder(values);
        assertThrows(org.springframework.boot.context.properties.bind.BindException.class,
                () -> binder.bind("", Bindable.of(CapabilityCompositionProperties.class),
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
                    () -> binder.bind("", Bindable.of(CapabilityCompositionProperties.class)));
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
        var mapper = new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl()));
        ClientPropertiesMapStructMapper clients = org.mapstruct.factory.Mappers.getMapper(
                com.cloud.gateway.infrastructure.policy.mapstruct.ClientPropertiesMapStructMapper.class);
        CapabilityCompositionPropertiesMapStructMapper policyConfiguration = org.mapstruct.factory.Mappers.getMapper(
                com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionPropertiesMapStructMapper.class);
        var content = new IngressContent(
                mapper.toDomainApps(new AppPropertiesMapStructMapperImpl().toStored(properties.getApps())),
                mapper.toDomainClients(clients.toStored(properties.getClients())),
                mapper.toDomainEndpointRegistry(new EndpointPropertiesMapStructMapperImpl().toStored(properties.getEndpoints())),
                mapper.toDomainPolicies(policyConfiguration.toStored(properties.getPolicies())));
        var hasher = new CanonicalAccessSnapshotHasher(new DefaultCanonicalPolicyContentMapper(new CapabilityCompositionStorageMapStructMapperImpl()), new ObjectMapper());
        assertNotNull(new IngressSnapshot(new SnapshotVersion(1), content, hasher.hash(content),
                Instant.parse("2026-09-08T00:00:00Z")));
        var admin = content.endpointRegistry().find(new EndpointId("GATEWAY.ADMIN")).orElseThrow();
        assertEquals("gateway-admin", admin.routeId().value());
        assertTrue(admin.access().requiredAudiences().contains("gateway"));
        assertEquals(3, content.capabilityComposition().bundles().size());
    }
}

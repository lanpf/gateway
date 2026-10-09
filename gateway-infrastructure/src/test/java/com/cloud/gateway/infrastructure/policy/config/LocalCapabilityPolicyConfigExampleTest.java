package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.DefaultCanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityPolicyConfigStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractAccessSnapshotDocumentMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractAppPropertiesMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractEndpointPropertiesMapStructMapperImpl;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractClientPropertiesMapStructMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractClientPropertiesMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityPolicyConfigPropertiesMapStructMapper;
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

class LocalCapabilityPolicyConfigExampleTest {
    @Test void shouldRejectDuplicateKeysInLocalYamlBeforeBinding() {
        String yaml = """
                gateway:
                  access-definition:
                    policies:
                      bindings:
                        all: {binding-id: all, bundle-id: one}
                        all: {binding-id: all, bundle-id: two}
                """;
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(
                yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(org.yaml.snakeyaml.constructor.DuplicateKeyException.class,
                () -> new YamlPropertySourceLoader().load("duplicate-bindings", resource));
    }

    @Test void shouldRejectOldMappingFieldDuringStrictLocalBinding() {
        var values = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("bundles[standard].bundle-id", "standard",
                        "bundles[standard].headers.trusted-mappings[X-Gateway-App]", "APP_ID",
                        "bundles.standard.capabilities.header-apply.trusted-headers[0]", "APP_ID"));
        var binder = new Binder(values);
        assertThrows(org.springframework.boot.context.properties.bind.BindException.class,
                () -> binder.bind("", Bindable.of(CapabilityPolicyConfigProperties.class),
                        new org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler(
                                org.springframework.boot.context.properties.bind.BindHandler.DEFAULT)));
    }

    @Test void shouldRejectRemovedTrustedSourceValuesDuringLocalBinding() {
        for (String source : java.util.List.of("VERIFIED_CLIENT_ID", "SUBJECT_ID", "LEGACY_CLIENT_APP_ID")) {
            var values = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                    java.util.Map.of("bundles[standard].bundle-id", "standard",
                            "bundles[standard].capabilities.header-apply.trustedHeaders[0]", source));
            var binder = new Binder(values);
            assertThrows(IllegalArgumentException.class,
                    () -> {
                        CapabilityPolicyConfigProperties properties = binder.bind("", Bindable.of(CapabilityPolicyConfigProperties.class)).orElseThrow(AssertionError::new);
                        com.cloud.gateway.infrastructure.policy.StoredCapabilityPolicyConfig stored = new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityPolicyConfigPropertiesMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())).toStored(properties);
                        com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(
                                BuiltInCapabilityCatalog.validator(),
                                new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new ObjectMapper())
                                .decode(stored.bundles().get("standard").capabilities());
                    });
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
        var mapper = new DefaultStoredAccessSnapshotMapper(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AbstractAccessSnapshotDocumentMapStructMapperImpl(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))));
        AbstractClientPropertiesMapStructMapper clients = new AbstractClientPropertiesMapStructMapperImpl();
        AbstractCapabilityPolicyConfigPropertiesMapStructMapper policyConfiguration = new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityPolicyConfigPropertiesMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()));
        var content = new IngressContent(
                mapper.toDomainApps(new AbstractAppPropertiesMapStructMapperImpl().toStored(properties.getApps())),
                mapper.toDomainClients(clients.toStored(properties.getClients())),
                mapper.toDomainEndpointRegistry(new AbstractEndpointPropertiesMapStructMapperImpl().toStored(properties.getEndpoints())),
                mapper.toDomainPolicies(policyConfiguration.toStored(properties.getPolicies())));
        var hasher = new CanonicalAccessSnapshotHasher(new DefaultCanonicalPolicyContentMapper(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))), new ObjectMapper());
        assertNotNull(new IngressSnapshot(new SnapshotVersion(1), content, hasher.hash(content),
                Instant.parse("2026-09-08T00:00:00Z")));
        var admin = content.endpoints().find(new EndpointId("GATEWAY.ADMIN")).orElseThrow();
        assertEquals("gateway-admin", admin.getRouteId().value());
        assertTrue(admin.getAccess().requiredAudiences().contains("gateway"));
        assertEquals(3, content.policyConfig().getBundles().size());
    }
}

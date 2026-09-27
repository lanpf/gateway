package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StoredPolicyDefinitionCodecTest {
    private static final String PACKAGED_EXAMPLE_HASH =
            "59344a56435c27761724b84594512088c75724898088bc43fe779c6ee34d1f4d";

    @Test
    void shouldDecodePackagedDefinitionExamples() throws IOException {
        StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
                new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())))), new ObjectMapper().findAndRegisterModules(),
                HeaderAdmissionRules.registeredOnly());

        IngressContent decoded = new IngressContent(
                codec.decodeApps(resource("gateway-app-capabilities.yaml")),
                codec.decodeClients(resource("gateway-client-capabilities.yaml")),
                codec.decodeEndpointRegistry(resource("gateway-endpoints.yaml")),
                codec.decodeCapabilityComposition(resource("gateway-access-configuration.yaml")));

        assertEquals(PACKAGED_EXAMPLE_HASH, PolicyTestFixtures.hasher().hash(decoded));
        assertTrue(decoded.endpoints().find(new EndpointId("USER_AUTH.LOGIN")).orElseThrow().enabled());
        java.util.Set<String> managementMatchers = decoded.endpoints()
                .find(new EndpointId("GATEWAY.ADMIN")).orElseThrow().matchers().stream()
                .map(matcher -> matcher.httpMethod() + " " + matcher.pathPattern())
                .collect(java.util.stream.Collectors.toSet());
        // These literals mirror GatewayPathApiConstants. That module is an outer layer this module
        // must not depend on, so the paths cannot be imported here — they have to be kept in step by
        // hand. A rename that touches only one side silently passes, which is how the paths drifted
        // to /admin/gateway/capabilities once already.
        assertEquals(java.util.Set.of(
                "PUT /admin/gateway/policies/app",
                "PUT /admin/gateway/policies/client-access",
                "PUT /admin/gateway/policies/client-credential",
                "PUT /admin/gateway/policies/endpoint-access",
                "PUT /admin/gateway/policies/configuration",
                "POST /admin/gateway/snapshot/publish",
                "POST /admin/gateway/snapshot/rollback",
                "GET /admin/gateway/policies/app",
                "GET /admin/gateway/policies/client-access",
                "GET /admin/gateway/policies/endpoint-access",
                "GET /admin/gateway/snapshot"), managementMatchers);
    }

    private String resource(String name) throws IOException {
        try (java.io.InputStream input = getClass().getResourceAsStream("/examples/definitions/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.header.HeaderRules;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StoredPolicyDefinitionCodecTest {
    private static final String PACKAGED_EXAMPLE_HASH =
            "f10716f0b79522fbb277e9961c8bb42d6cbcb958525c92f07176d6ee700aa47c";

    @Test
    void shouldDecodePackagedDefinitionExamples() throws IOException {
        StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
                new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl())), new ObjectMapper().findAndRegisterModules(),
                HeaderRules.registeredOnly());

        IngressContent decoded = new IngressContent(
                codec.decodeApps(resource("gateway-app-policies.yaml")),
                codec.decodeClients(resource("gateway-client-policies.yaml")),
                codec.decodeEndpointRegistry(resource("gateway-endpoints.yaml")),
                codec.decodeCapabilityComposition(resource("gateway-policy-configuration.yaml")));

        assertEquals(PACKAGED_EXAMPLE_HASH, PolicyTestFixtures.hasher().hash(decoded));
        assertTrue(decoded.endpointRegistry().find(new EndpointId("USER_AUTH.LOGIN")).orElseThrow().enabled());
        java.util.Set<String> managementMatchers = decoded.endpointRegistry()
                .find(new EndpointId("GATEWAY.ADMIN")).orElseThrow().matchers().stream()
                .map(matcher -> matcher.httpMethod() + " " + matcher.pathPattern())
                .collect(java.util.stream.Collectors.toSet());
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

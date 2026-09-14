package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StoredPolicyDefinitionCodecTest {
    @Test
    void shouldDecodePackagedDefinitionExamples() throws IOException {
        StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
                new StoredAccessSnapshotMapper(), new ObjectMapper().findAndRegisterModules(),
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());

        IngressAccessContent decoded = new IngressAccessContent(
                codec.decodeEndpointRegistry(resource("gateway-endpoints.yaml")),
                codec.decodeApps(resource("gateway-app-policies.yaml")),
                codec.decodeClients(resource("gateway-client-policies.yaml")),
                codec.decodeEndpointAccess(resource("gateway-endpoint-access-policies.yaml")),
                codec.decodePolicyConfiguration(resource("gateway-policy-configuration.yaml")));

        org.junit.jupiter.api.Assertions.assertEquals("7aa5c7488c3e952e435d501549690e0be1c254ec3c0c1da4669cebff4675adb0", PolicyTestFixtures.hasher().hash(decoded));
        assertTrue(decoded.endpointPolicies().get(new EndpointId("USER_AUTH.LOGIN")).enabled());
    }

    private String resource(String name) throws IOException {
        try (java.io.InputStream input = getClass().getResourceAsStream("/examples/definitions/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

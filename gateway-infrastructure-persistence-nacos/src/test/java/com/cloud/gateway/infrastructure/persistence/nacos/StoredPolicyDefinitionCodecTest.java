package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StoredPolicyDefinitionCodecTest {
    @Test
    void shouldDecodePackagedDefinitionExamples() throws IOException {
        StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
                new StoredPolicySnapshotMapper(), new ObjectMapper().findAndRegisterModules());

        IngressPolicyContent decoded = new IngressPolicyContent(
                codec.decodeEndpointCatalog(resource("gateway-endpoints.yaml")),
                codec.decodeClientApps(resource("gateway-client-app-policies.yaml")),
                codec.decodeChannelAccess(resource("gateway-channel-access-policies.yaml")),
                codec.decodeChannelCredentials(resource("gateway-channel-credential-policies.yaml")),
                codec.decodeRouteAccess(resource("gateway-route-access-policies.yaml")));

        assertTrue(decoded.routeAccessPolicies().get(new EndpointId("USER_AUTH.LOGIN")).enabled());
    }

    private String resource(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/examples/definitions/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

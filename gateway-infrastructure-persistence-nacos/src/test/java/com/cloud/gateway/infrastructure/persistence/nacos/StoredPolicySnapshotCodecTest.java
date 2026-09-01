package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;
import org.junit.jupiter.api.Test;

class StoredPolicySnapshotCodecTest {
    private final StoredPolicySnapshotCodec codec = PolicyTestFixtures.codec();

    @Test
    void shouldRoundTripCompletePolicySnapshot() {
        IngressPolicySnapshot source = PolicyTestFixtures.snapshot(7, true);

        IngressPolicySnapshot decoded = codec.decodeSnapshot(codec.encodeSnapshot(source));

        assertEquals(7, decoded.snapshotVersion());
        assertEquals(source.contentHash(), decoded.contentHash());
        assertEquals("user-auth", decoded.content().endpointCatalog()
                .find(new EndpointId("USER_AUTH.LOGIN")).orElseThrow().deliveryRouteId());
        assertEquals(true, decoded.content().routeAccessPolicies()
                .get(new EndpointId("USER_AUTH.LOGIN")).enabled());
    }

    @Test
    void shouldRejectContentWhoseBusinessHashDoesNotMatch() {
        IngressPolicySnapshot snapshot = PolicyTestFixtures.snapshot(1, true);
        String content = codec.encodeSnapshot(snapshot).replace(snapshot.contentHash(), "invalid");

        assertThrows(InfrastructureException.class, () -> codec.decodeSnapshot(content));
    }

    @Test
    void shouldDecodePackagedBootstrapExample() throws IOException {
        String content;
        try (var input = getClass().getResourceAsStream("/examples/gateway-ingress-policy.yaml")) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        IngressPolicySnapshot decoded = codec.decodeSnapshot(content);

        assertEquals(1, decoded.snapshotVersion());
        assertEquals("3e52004bd19388a8c173c265b9678d752203acb571eef80e2240dafba31e64a5",
                decoded.contentHash());
    }
}

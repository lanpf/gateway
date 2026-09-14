package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import org.junit.jupiter.api.Test;

class StoredIngressAccessSnapshotCodecTest {
    private final StoredAccessSnapshotCodec codec = PolicyTestFixtures.codec();

    @Test
    void shouldRoundTripCompleteAccessSnapshot() {
        IngressAccessSnapshot source = PolicyTestFixtures.snapshot(7, true);

        IngressAccessSnapshot decoded = codec.decodeSnapshot(codec.encodeSnapshot(source));

        assertEquals(7, decoded.version());
        assertEquals(source.contentHash(), decoded.contentHash());
        assertEquals("user-auth", decoded.content().endpointRegistry()
                .find(new EndpointId("USER_AUTH.LOGIN")).orElseThrow().routeId());
        assertEquals(true, decoded.content().endpointPolicies()
                .get(new EndpointId("USER_AUTH.LOGIN")).enabled());
    }

    @Test
    void shouldRejectContentWhoseBusinessHashDoesNotMatch() {
        IngressAccessSnapshot snapshot = PolicyTestFixtures.snapshot(1, true);
        String content = codec.encodeSnapshot(snapshot).replace(snapshot.contentHash(), "invalid");

        assertThrows(InfrastructureException.class, () -> codec.decodeSnapshot(content));
    }

    @Test
    void shouldDecodePackagedBootstrapExample() throws IOException {
        String content;
        try (java.io.InputStream input = getClass().getResourceAsStream("/examples/gateway-policy.yaml")) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        IngressAccessSnapshot decoded = codec.decodeSnapshot(content);

        assertEquals(1, decoded.version());
        assertEquals("7aa5c7488c3e952e435d501549690e0be1c254ec3c0c1da4669cebff4675adb0",
                decoded.contentHash());
    }
}

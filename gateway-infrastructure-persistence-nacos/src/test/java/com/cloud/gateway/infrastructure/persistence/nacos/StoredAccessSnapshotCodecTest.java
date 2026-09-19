package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StoredAccessSnapshotCodecTest {
    private static final String PACKAGED_EXAMPLE_HASH =
            "f10716f0b79522fbb277e9961c8bb42d6cbcb958525c92f07176d6ee700aa47c";

    private final StoredAccessSnapshotCodec codec = PolicyTestFixtures.codec();

    @Test
    void shouldRoundTripCompleteAccessSnapshot() {
        IngressSnapshot source = PolicyTestFixtures.snapshot(7, true);

        IngressSnapshot decoded = codec.decodeSnapshot(codec.encodeSnapshot(source));

        assertEquals(7, decoded.version().value());
        assertEquals(source.contentHash(), decoded.contentHash());
        assertEquals("user-auth", decoded.content().endpointRegistry()
                .find(new EndpointId("USER_AUTH.LOGIN")).orElseThrow().routeId().value());
        assertEquals(true, decoded.content().endpointRegistry()
                .find(new EndpointId("USER_AUTH.LOGIN")).orElseThrow().enabled());
    }

    @Test
    void shouldRejectContentWhoseBusinessHashDoesNotMatch() {
        IngressSnapshot snapshot = PolicyTestFixtures.snapshot(1, true);
        String content = codec.encodeSnapshot(snapshot).replace(snapshot.contentHash(), "invalid");

        assertThrows(InfrastructureException.class, () -> codec.decodeSnapshot(content));
    }

    @Test
    void shouldDecodePackagedBootstrapExample() throws IOException {
        String content;
        try (java.io.InputStream input = getClass().getResourceAsStream("/examples/gateway-policy.yaml")) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        IngressSnapshot decoded = codec.decodeSnapshot(content);

        assertEquals(1, decoded.version().value());
        assertEquals(PACKAGED_EXAMPLE_HASH, decoded.contentHash());
        assertThrows(InfrastructureException.class, () -> codec.decodeSnapshot(
                content.replace("/admin/gateway/policies/app", "/admin/gateway/capabilityComposition/app")));
    }
}

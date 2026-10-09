package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StoredAccessSnapshotCodecTest {
    /** Mirrors {@code GatewayPathApiConstants.ADMIN_POLICIES_APP}; the api module is an outer layer. */
    private static final String ADMIN_APP_PATH = "/admin/gateway/policies/app";
    private static final String PACKAGED_EXAMPLE_HASH =
            "6c722f630612c4b7c32bb871be4b2bb79340bbc366373c4b30ed30b9e6380d98";

    private final StoredAccessSnapshotCodec codec = PolicyTestFixtures.codec();

    @Test
    void shouldRoundTripCompleteAccessSnapshot() {
        IngressSnapshot source = PolicyTestFixtures.snapshot(7, true);

        IngressSnapshot decoded = codec.decodeSnapshot(codec.encodeSnapshot(source));

        assertEquals(7, decoded.version().value());
        assertEquals(source.contentHash(), decoded.contentHash());
        assertEquals("user-auth", decoded.content().endpoints()
                .find(new EndpointId("USER_AUTH.LOGIN")).orElseThrow().getRouteId().value());
        assertEquals(true, decoded.content().endpoints()
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
        try (java.io.InputStream input = getClass().getResourceAsStream("/examples/gateway-access.yaml")) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        IngressSnapshot decoded = codec.decodeSnapshot(content);

        assertEquals(1, decoded.version().value());
        assertEquals(PACKAGED_EXAMPLE_HASH, decoded.contentHash());
        // The example must carry the real admin path, and tampering with it must be rejected by the
        // content hash. Both halves matter: if the example drifted, the replacement below would
        // silently become a no-op and the hash guard would go unexercised.
        assertTrue(content.contains(ADMIN_APP_PATH));
        assertThrows(InfrastructureException.class, () -> codec.decodeSnapshot(
                content.replace(ADMIN_APP_PATH, ADMIN_APP_PATH + "-tampered")));
    }
}

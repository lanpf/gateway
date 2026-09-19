package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.configuration.definition.AccessDefinitionReader;
import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.policy.bundle.CapabilityBundle;
import com.cloud.gateway.domain.policy.bundle.CapabilityBundleId;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import com.cloud.gateway.domain.policy.header.HeaderRules;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropertiesAccessSnapshotCapturerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldAssembleImmutableInitialSnapshotThroughApplicationOnce() {
        IngressContent content = content(CapabilityComposition.empty());
        AtomicInteger loads = new AtomicInteger();
        PropertiesAccessSnapshotCapturer provider = new PropertiesAccessSnapshotCapturer(reader(content, loads),
                new com.cloud.gateway.application.configuration.publication.AccessSnapshotAssembler(value -> "content-digest", HeaderRules.registeredOnly()), CLOCK);
        assertEquals(1, provider.capture().snapshot().version().value());
        assertEquals(CLOCK.instant(), provider.capture().snapshot().publishedAt());
        assertEquals("properties:content-digest", provider.capture().sourceRevision().value());
        assertSame(provider.capture(), provider.capture());
        assertEquals(4, loads.get());
    }

    @Test
    void shouldValidateDeploymentHeadersBeforeExposingLocalSnapshot() {
        CapabilityBundleId id = new CapabilityBundleId("headers");
        HeaderPolicy headers = new HeaderPolicy(Set.of(), Map.of("X-Unregistered", "value"), Set.of(), null);
        CapabilityComposition composition = new CapabilityComposition(
                Map.of(id, new CapabilityBundle(id, null, null, null, null, headers)), List.of());
        assertThrows(DomainException.class, () -> new PropertiesAccessSnapshotCapturer(
                reader(content(composition), new AtomicInteger()),
                new com.cloud.gateway.application.configuration.publication.AccessSnapshotAssembler(value -> "digest", HeaderRules.registeredOnly()), CLOCK));
    }

    @Test
    void shouldValidateCrossDefinitionReferencesBeforeExposingLocalSnapshot() {
        IngressContent invalid = new IngressContent(new AppRegistry(List.of()),
                new ClientRegistry(List.of(new Client(new ClientId("client"), ClientStatus.ENABLED,
                        new ClientAccess(Set.of(new AppId("missing")), null)))),
                new EndpointRegistry(List.of()), CapabilityComposition.empty());
        assertThrows(DomainException.class, () -> new PropertiesAccessSnapshotCapturer(reader(invalid, new AtomicInteger()),
                new com.cloud.gateway.application.configuration.publication.AccessSnapshotAssembler(value -> "digest", HeaderRules.registeredOnly()), CLOCK));
    }

    private static IngressContent content(CapabilityComposition policies) {
        return new IngressContent(new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of()), policies);
    }

    private static AccessDefinitionReader reader(IngressContent content, AtomicInteger loads) {
        return new AccessDefinitionReader(() -> versioned(content.endpointRegistry(), loads),
                () -> versioned(content.appRegistry(), loads), () -> versioned(content.clientRegistry(), loads),
                () -> versioned(content.capabilityComposition(), loads));
    }

    private static <T> RevisionedAccessDefinition<T> versioned(T value, AtomicInteger loads) {
        loads.incrementAndGet();
        return new RevisionedAccessDefinition<>(value, new com.cloud.gateway.domain.ingress.snapshot.Revision("value"));
    }
}

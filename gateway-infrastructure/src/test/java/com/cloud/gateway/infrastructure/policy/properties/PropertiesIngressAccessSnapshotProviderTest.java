package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.PolicyDefinitionReader;
import com.cloud.gateway.application.policy.PolicySnapshotAssembler;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.policy.bundle.PolicyBundle;
import com.cloud.gateway.domain.policy.bundle.PolicyBundleId;
import com.cloud.gateway.domain.policy.bundle.PolicyComposition;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.app.AppId;
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

class PropertiesIngressAccessSnapshotProviderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldAssembleImmutableInitialSnapshotThroughApplicationOnce() {
        IngressAccessContent content = content(PolicyComposition.empty());
        AtomicInteger loads = new AtomicInteger();
        PropertiesPolicySnapshotProvider provider = new PropertiesPolicySnapshotProvider(reader(content, loads),
                new PolicySnapshotAssembler(value -> "content-digest", HeaderRules.registeredOnly()), CLOCK);
        assertEquals(0, provider.current().snapshot().version());
        assertEquals(CLOCK.instant(), provider.current().snapshot().publishedAt());
        assertEquals("properties:content-digest", provider.current().sourceRevision());
        assertSame(provider.current(), provider.current());
        assertEquals(5, loads.get());
    }

    @Test
    void shouldValidateDeploymentHeadersBeforeExposingLocalSnapshot() {
        PolicyBundleId id = new PolicyBundleId("headers");
        HeaderPolicy headers = new HeaderPolicy(Set.of(), Map.of("X-Unregistered", "value"), Set.of(), null);
        PolicyComposition composition = new PolicyComposition(
                Map.of(id, new PolicyBundle(id, null, null, null, null, headers)), List.of());
        assertThrows(DomainException.class, () -> new PropertiesPolicySnapshotProvider(
                reader(content(composition), new AtomicInteger()),
                new PolicySnapshotAssembler(value -> "digest", HeaderRules.registeredOnly()), CLOCK));
    }

    @Test
    void shouldValidateCrossDefinitionReferencesBeforeExposingLocalSnapshot() {
        ClientId id = new ClientId("client");
        IngressAccessContent invalid = new IngressAccessContent(new EndpointRegistry(List.of()), Map.of(),
                Map.of(id, new ClientPolicy(id, Set.of(new AppId("missing")))), Map.of());
        assertThrows(DomainException.class, () -> new PropertiesPolicySnapshotProvider(reader(invalid, new AtomicInteger()),
                new PolicySnapshotAssembler(value -> "digest", HeaderRules.registeredOnly()), CLOCK));
    }

    private static IngressAccessContent content(PolicyComposition policies) {
        return new IngressAccessContent(new EndpointRegistry(List.of()), Map.of(), Map.of(), Map.of(), policies);
    }

    private static PolicyDefinitionReader reader(IngressAccessContent content, AtomicInteger loads) {
        return new PolicyDefinitionReader(() -> versioned(content.endpointRegistry(), loads),
                () -> versioned(content.appPolicies(), loads), () -> versioned(content.clients(), loads),
                () -> versioned(content.endpointPolicies(), loads), () -> versioned(content.policies(), loads));
    }

    private static <T> VersionedPolicyDefinition<T> versioned(T value, AtomicInteger loads) {
        loads.incrementAndGet();
        return new VersionedPolicyDefinition<>(value, "revision");
    }
}

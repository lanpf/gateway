package com.cloud.gateway.domain.capability.policy;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Which overlapping bindings are ambiguous. Two bindings for the same bundle are one configuration
 * source; two different bundles competing for an overridden node are not.
 */
class CapabilityPolicyConfigConflictTest {
    private static final TimeoutConfig TIMEOUT =
            new TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));

    @Test
    void shouldAcceptTheSameBundleReachedByTwoOverlappingBindings() {
        CapabilityBundleId shared = new CapabilityBundleId("shared");

        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(bundle(shared)),
                List.of(tagBinding("public-tag", shared, "public"), tagBinding("mobile-tag", shared, "mobile")));

        // Same source, so whichever binding wins the request resolves to the same timeout: idempotent.
        IngressContent content = content();
        assertDoesNotThrow(() -> BuiltInCapabilityCatalog.validator()
                .validate(policyConfig, content.apps(), content.clients(), content.endpoints()));
    }

    @Test
    void shouldRejectTwoDistinctBundlesCompetingForTheSameNode() {
        CapabilityBundleId first = new CapabilityBundleId("first");
        CapabilityBundleId second = new CapabilityBundleId("second");

        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(java.util.List.of(bundle(first), bundle(second)),
                List.of(tagBinding("public-tag", first, "public"), tagBinding("mobile-tag", second, "mobile")));

        IngressContent content = content();
        assertThrows(DomainException.class,
                () -> BuiltInCapabilityCatalog.validator()
                        .validate(policyConfig, content.apps(), content.clients(), content.endpoints()));
    }

    private static CapabilityBundle bundle(CapabilityBundleId id) {
        return new CapabilityBundle(id, java.util.List.of(TIMEOUT));
    }

    private static CapabilityBinding tagBinding(String id, CapabilityBundleId bundleId, String tag) {
        return new CapabilityBinding(new CapabilityBindingId(id), bundleId, null, null, Set.of(tag),
                null, null, null);
    }

    /** One endpoint carrying both tags, so the two same-specificity bindings overlap on it. */
    private static IngressContent content() {
        Endpoint endpoint = new Endpoint(new EndpointId("endpoint"), new ServiceId("service"),
                new RouteId("route"), List.of(new EndpointMatcher("GET", "/read")),
                Set.of("public", "mobile"), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));
        return new IngressContent(new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of(endpoint)), CapabilityPolicyConfig.empty());
    }
}

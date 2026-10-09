package com.cloud.gateway.domain.capability.policy;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.capability.header.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.capability.header.CookieForwardingMode;
import com.cloud.gateway.domain.capability.header.CredentialForwarding;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityPolicyConfigValidationTest {
    @Test
    void shouldRejectNullClientAppElementWithFrameworkExceptionBeforeCopy() {
        assertThrows(FrameworkException.class,
                () -> new ClientAccess(Collections.singleton(null), null));
    }

    @Test
    void shouldRejectNullScopeAudienceAndForwardingCookieElementsWithFrameworkException() {
        assertThrows(FrameworkException.class, () -> endpointPolicy(Collections.singleton(null), Set.of("api")));
        assertThrows(FrameworkException.class, () -> endpointPolicy(Set.of(), Collections.singleton(null)));
        assertThrows(FrameworkException.class, () -> new CredentialForwarding(AuthorizationHeaderForwardingMode.STRIP,
                CookieForwardingMode.ALLOW_LIST, Collections.singleton(null)));
    }

    @Test
    void shouldRejectNullMatcherAndRegistryElementsWithFrameworkException() {
        assertThrows(FrameworkException.class, () -> endpoint(Arrays.asList((EndpointMatcher) null)));
        assertThrows(FrameworkException.class, () -> new EndpointRegistry(Collections.singletonList(null)));
        assertThrows(FrameworkException.class, () -> new AppRegistry(Collections.singletonList(null)));
        assertThrows(FrameworkException.class, () -> new ClientRegistry(Collections.singletonList(null)));
    }

    @Test
    void shouldNormalizeMissingScopesAndAudiencesToEmptySets() {
        EndpointAccess policy = endpointPolicy(null, null);

        assertEquals(Set.of(), policy.requiredScopes());
        assertEquals(Set.of(), policy.requiredAudiences());
    }

    @Test
    void shouldKeepDefensiveCopiesAfterElementValidation() {
        Set<AppId> apps = new HashSet<>();
        apps.add(new AppId("app"));
        ClientAccess client = new ClientAccess(apps, null);
        apps.clear();
        assertEquals(1, client.allowedApps().size());

        Set<String> tags = new HashSet<>();
        tags.add("public-read");
        Endpoint endpoint = new Endpoint(new EndpointId("endpoint"), new ServiceId("service"),
                new RouteId("route"), List.of(new EndpointMatcher("GET", "/x")), tags, EndpointStatus.ENABLED,
                endpointPolicy(Set.of(), Set.of()));
        tags.clear();
        assertEquals(Set.of("public-read"), endpoint.getTags());
        assertThrows(UnsupportedOperationException.class, () -> endpoint.getTags().clear());
    }

    @Test
    void shouldKeepContentRegistriesImmutable() {
        IngressContent content = new IngressContent(
                new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of(endpoint(List.of(new EndpointMatcher("GET", "/x"))))),
                CapabilityPolicyConfig.empty());

        assertThrows(UnsupportedOperationException.class, () -> content.apps().asMap().clear());
    }

    private static EndpointAccess endpointPolicy(Set<String> scopes, Set<String> audiences) {
        return new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                SubjectAuthenticationRequirement.ANONYMOUS, scopes, audiences);
    }

    private static Endpoint endpoint(List<EndpointMatcher> matchers) {
        return new Endpoint(new EndpointId("endpoint"), new ServiceId("service"), new RouteId("route"),
                matchers, Set.of(), EndpointStatus.ENABLED, endpointPolicy(Set.of(), Set.of()));
    }
}

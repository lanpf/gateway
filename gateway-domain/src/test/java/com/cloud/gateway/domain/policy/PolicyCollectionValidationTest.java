package com.cloud.gateway.domain.policy;

import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.common.DomainException;
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
import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyCollectionValidationTest {
    @Test
    void shouldRejectNullClientAppElementWithDomainExceptionBeforeCopy() {
        assertThrows(DomainException.class,
                () -> new ClientAccess(Collections.singleton(null), null));
    }

    @Test
    void shouldRejectNullScopeAudienceAndForwardingCookieElementsWithDomainException() {
        assertThrows(DomainException.class, () -> endpointPolicy(Collections.singleton(null), Set.of("api")));
        assertThrows(DomainException.class, () -> endpointPolicy(Set.of(), Collections.singleton(null)));
        assertThrows(DomainException.class, () -> new CredentialForwarding(AuthorizationHeaderForwardingMode.STRIP,
                CookieForwardingMode.ALLOW_LIST, Collections.singleton(null)));
    }

    @Test
    void shouldRejectNullMatcherAndRegistryElementsWithDomainException() {
        assertThrows(DomainException.class, () -> endpoint(Arrays.asList((EndpointMatcher) null)));
        assertThrows(DomainException.class, () -> new EndpointRegistry(Collections.singletonList(null)));
        assertThrows(DomainException.class, () -> new AppRegistry(Collections.singletonList(null)));
        assertThrows(DomainException.class, () -> new ClientRegistry(Collections.singletonList(null)));
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
        assertEquals(Set.of("public-read"), endpoint.tags());
        assertThrows(UnsupportedOperationException.class, () -> endpoint.tags().clear());
    }

    @Test
    void shouldKeepContentRegistriesImmutable() {
        IngressAccessContent content = new IngressAccessContent(
                new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of(endpoint(List.of(new EndpointMatcher("GET", "/x"))))),
                PolicyConfig.empty());

        assertThrows(UnsupportedOperationException.class, () -> content.appRegistry().asMap().clear());
    }

    private static EndpointAccess endpointPolicy(Set<String> scopes, Set<String> audiences) {
        return new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                SubjectAuthenticationRequirement.OPTIONAL, scopes, audiences);
    }

    private static Endpoint endpoint(List<EndpointMatcher> matchers) {
        return new Endpoint(new EndpointId("endpoint"), new ServiceId("service"), new RouteId("route"),
                matchers, Set.of(), EndpointStatus.ENABLED, endpointPolicy(Set.of(), Set.of()));
    }
}

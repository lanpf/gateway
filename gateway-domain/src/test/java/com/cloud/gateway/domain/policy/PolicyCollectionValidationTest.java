package com.cloud.gateway.domain.policy;

import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolicyCollectionValidationTest {
    @Test
    void shouldRejectNullClientAppElementWithDomainExceptionBeforeCopy() {
        assertThrows(DomainException.class,
                () -> new ClientPolicy(new ClientId("client"), Collections.singleton(null)));
    }

    @Test
    void shouldRejectNullScopeAudienceAndCookieElementsWithDomainException() {
        assertThrows(DomainException.class, () -> endpoint(Collections.singleton(null), Set.of("api")));
        assertThrows(DomainException.class, () -> endpoint(Set.of(), Collections.singleton(null)));
        assertThrows(DomainException.class, () -> new CredentialForwarding(AuthorizationHeaderForwardingMode.STRIP,
                CookieForwardingMode.ALLOW_LIST, Collections.singleton(null)));
    }

    @Test
    void shouldRejectNullPolicyMapKeysAndValuesWithDomainException() {
        EndpointRegistry registry = new EndpointRegistry(List.of());
        AppPolicy app = new AppPolicy(new AppId("app"), "WEB", Set.of(), PolicyStatus.ENABLED);
        assertThrows(DomainException.class, () -> new IngressAccessContent(registry,
                Collections.singletonMap(null, app), Map.of(), Map.of()));
        assertThrows(DomainException.class, () -> new IngressAccessContent(registry,
                Collections.singletonMap(app.appId(), null), Map.of(), Map.of()));
        assertThrows(DomainException.class, () -> new IngressAccessContent(registry,
                Map.of(), Collections.singletonMap(new ClientId("client"), null), Map.of()));
        assertThrows(DomainException.class, () -> new IngressAccessContent(registry,
                Map.of(), Map.of(), Collections.singletonMap(new EndpointId("endpoint"), null)));
    }

    @Test
    void shouldKeepDefensiveCopiesAfterElementValidation() {
        Set<AppId> apps = new HashSet<>();
        apps.add(new AppId("app"));
        ClientPolicy client = new ClientPolicy(new ClientId("client"), apps);
        apps.clear();
        assertEquals(1, client.allowedApps().size());
        Map<ClientId, ClientPolicy> clients = new HashMap<>();
        clients.put(client.clientId(), client);
        IngressAccessContent content = new IngressAccessContent(new EndpointRegistry(List.of()), Map.of(), clients, Map.of());
        clients.clear();
        assertEquals(1, content.clients().size());
        assertThrows(UnsupportedOperationException.class, () -> content.clients().clear());
    }

    private static EndpointPolicy endpoint(Set<String> scopes, Set<String> audiences) {
        return new EndpointPolicy(new EndpointId("endpoint"), ClientVerificationRequirement.OPTIONAL,
                ClientVerificationMode.IGNORE, SubjectAuthencationRequirement.HOST_SESSION_REQUIRED,
                scopes, audiences, CredentialForwarding.stripAll(), EndpointStatus.ENABLED);
    }
}

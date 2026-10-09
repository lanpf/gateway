package com.cloud.gateway.domain.capability.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.framework.core.SubjectType;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.VerifiedApp;
import com.cloud.gateway.domain.ingress.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.ingress.access.client.VerifiedClient;
import com.cloud.gateway.domain.ingress.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.ingress.access.subject.SessionId;
import com.cloud.gateway.domain.ingress.access.subject.SubjectId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CapabilityContextTest {
    private static final AppId APP = new AppId("app");
    private static final RequestEvidence EVIDENCE =
            new RequestEvidence("GET", "/read", null, APP, new ClientId("declared"), null, "ip");
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("read"), new ServiceId("service"),
            new RouteId("route"), List.of(new EndpointMatcher("GET", "/read")), Set.of(), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL, SubjectAuthenticationRequirement.ANONYMOUS,
                    Set.of(), Set.of()));
    private static final AccessGrantedContext ANONYMOUS =
            new AccessGrantedContext(new VerifiedApp(APP, "web"), null, null);

    @Test
    void shouldNotTreatDeclaredIdentityAsAvailableTrustedFacts() {
        assertEquals(Set.of(), new CapabilityContext(CapabilitySlot.PRE_AUTH, EVIDENCE, null, null).facts());
        assertEquals(Set.of(CapabilityFact.ENDPOINT),
                new CapabilityContext(CapabilitySlot.PRE_AUTH, EVIDENCE, ENDPOINT, null).facts());
    }

    @Test
    void shouldDistinguishAnonymousGrantFromAuthenticatedIdentity() {
        for (CapabilitySlot slot : List.of(CapabilitySlot.POST_AUTH, CapabilitySlot.FORWARD)) {
            CapabilityContext context = new CapabilityContext(slot, EVIDENCE, ENDPOINT, ANONYMOUS);
            assertEquals(Set.of(CapabilityFact.ENDPOINT, CapabilityFact.ACCESS_GRANTED), context.facts());
            assertThrows(UnsupportedOperationException.class, () -> context.facts().clear());
        }
    }

    @Test
    void shouldDeriveClientAndSubjectFactsIndependently() {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        VerifiedClient client = new VerifiedClient(new ClientId("verified"), new CredentialId("credential"),
                ClientVerificationMode.REQUEST_SIGNATURE, now);
        AuthenticatedSubject subject = new AuthenticatedSubject(SubjectType.BROWSER_SESSION,
                new SubjectId("user"), new SessionId("session"), null, null, null, now.plusSeconds(60));
        assertEquals(Set.of(CapabilityFact.ENDPOINT, CapabilityFact.ACCESS_GRANTED, CapabilityFact.VERIFIED_CLIENT),
                new CapabilityContext(CapabilitySlot.POST_AUTH, EVIDENCE, ENDPOINT,
                        new AccessGrantedContext(ANONYMOUS.app(), client, null)).facts());
        assertEquals(Set.of(CapabilityFact.ENDPOINT, CapabilityFact.ACCESS_GRANTED, CapabilityFact.AUTHENTICATED_SUBJECT),
                new CapabilityContext(CapabilitySlot.POST_AUTH, EVIDENCE, ENDPOINT,
                        new AccessGrantedContext(ANONYMOUS.app(), null, subject)).facts());
    }

    @Test
    void shouldRejectMissingMandatoryInputsInsteadOfSkippingCapabilities() {
        for (CapabilitySlot slot : CapabilitySlot.values()) {
            assertThrows(FrameworkException.class, () -> new CapabilityContext(slot, null, ENDPOINT, null));
        }
        for (CapabilitySlot slot : List.of(CapabilitySlot.POST_AUTH, CapabilitySlot.FORWARD)) {
            assertThrows(FrameworkException.class, () -> new CapabilityContext(slot, EVIDENCE, null, ANONYMOUS));
            assertThrows(FrameworkException.class, () -> new CapabilityContext(slot, EVIDENCE, ENDPOINT, null));
        }
        assertThrows(FrameworkException.class,
                () -> new CapabilityContext(CapabilitySlot.PRE_AUTH, EVIDENCE, ENDPOINT, ANONYMOUS));
    }
}

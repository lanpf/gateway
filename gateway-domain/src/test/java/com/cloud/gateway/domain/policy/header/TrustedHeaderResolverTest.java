package com.cloud.gateway.domain.policy.header;

import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.SubjectType;
import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.domain.access.app.VerifiedApp;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.access.subject.SessionId;
import com.cloud.gateway.domain.access.subject.SubjectId;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.ClientId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrustedHeaderResolverTest {
    @Test
    void shouldKeepSupportedHeadersAndResolutionConsistentForEveryHeader() {
        Map<RequestHeader, String> expected = Map.of(
                RequestHeader.APP_ID, "web-app",
                RequestHeader.APP_PLATFORM, "WEB",
                RequestHeader.CLIENT_ID, "verified-client",
                RequestHeader.USER_ID, "user-1",
                RequestHeader.SESSION_ID, "session-1",
                RequestHeader.SUBJECT_TYPE, "HOST_SESSION");
        IngressAccessGrantedContext context = authenticatedContext();
        Set<RequestHeader> supported = TrustedHeaderResolver.supportedHeaders();

        assertEquals(6, supported.size());
        assertEquals(expected.keySet(), supported);
        for (RequestHeader source : RequestHeader.values()) {
            assertEquals(expected.containsKey(source), TrustedHeaderResolver.supports(source), source.name());
            assertEquals(expected.containsKey(source), supported.contains(source), source.name());
            if (expected.containsKey(source)) {
                assertEquals(Optional.of(expected.get(source)), TrustedHeaderResolver.resolve(source, context));
            } else {
                assertEquals(Optional.empty(), TrustedHeaderResolver.resolve(source, context), source.name());
            }
        }
    }

    @Test
    void shouldNotResolveClientIdentityFromDeclarationAlone() {
        var context = new IngressAccessGrantedContext(app(), null, null);

        assertEquals(Optional.empty(), TrustedHeaderResolver.resolve(RequestHeader.CLIENT_ID, context));
        assertEquals(Optional.of("web-app"), TrustedHeaderResolver.resolve(RequestHeader.APP_ID, context));
        assertEquals(Optional.of("WEB"), TrustedHeaderResolver.resolve(RequestHeader.APP_PLATFORM, context));
    }

    @Test
    void shouldResolveClientIdentityOnlyFromVerifiedCredentialContext() {
        assertEquals(Optional.of("verified-client"),
                TrustedHeaderResolver.resolve(RequestHeader.CLIENT_ID, authenticatedContext()));
        var context = new IngressAccessGrantedContext(app(),
                new VerifiedClient(new ClientId("verified-client"), new CredentialId("client-1"),
                        ClientVerificationMode.REQUEST_SIGNATURE, Instant.now()), null);
        assertEquals(Optional.of("verified-client"), TrustedHeaderResolver.resolve(RequestHeader.CLIENT_ID, context));
    }

    @Test
    void shouldOmitSubjectAndClientValuesWhenTheirContextsAreMissing() {
        var context = new IngressAccessGrantedContext(app(), null, null);
        for (RequestHeader source : Set.of(RequestHeader.CLIENT_ID, RequestHeader.USER_ID,
                RequestHeader.SESSION_ID, RequestHeader.SUBJECT_TYPE)) {
            assertEquals(Optional.empty(), TrustedHeaderResolver.resolve(source, context), source.name());
        }
    }

    @Test
    void shouldExposeImmutableSupportedHeaders() {
        Set<RequestHeader> supported = TrustedHeaderResolver.supportedHeaders();
        assertThrows(UnsupportedOperationException.class, supported::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> supported.add(RequestHeader.APP_VERSION));
        assertThrows(UnsupportedOperationException.class,
                () -> supported.remove(RequestHeader.APP_ID));
        assertEquals(supported, TrustedHeaderResolver.supportedHeaders());
    }

    @Test
    void shouldReturnEmptyForNullSourceAndRejectMissingContext() {
        assertFalse(TrustedHeaderResolver.supports(null));
        assertEquals(Optional.empty(), TrustedHeaderResolver.resolve(null, authenticatedContext()));
        assertThrows(DomainException.class, () -> TrustedHeaderResolver.resolve(RequestHeader.APP_ID, null));
    }

    private static IngressAccessGrantedContext authenticatedContext() {
        return new IngressAccessGrantedContext(app(),
                new VerifiedClient(new ClientId("verified-client"), new CredentialId("client-1"),
                        ClientVerificationMode.REQUEST_SIGNATURE, Instant.now()),
                new AuthenticatedSubject(SubjectType.HOST_SESSION, new SubjectId("user-1"), new SessionId("session-1"),
                        "issuer", Set.of("gateway"), Set.of("admin"), Instant.parse("2030-01-01T00:00:00Z")));
    }

    private static VerifiedApp app() {
        return new VerifiedApp(new AppId("web-app"), "WEB");
    }
}

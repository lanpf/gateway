package com.cloud.gateway.domain.common;

import com.cloud.gateway.domain.app.*;
import com.cloud.gateway.domain.client.*;
import com.cloud.gateway.domain.endpoint.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AggregateLifecycleTest {
    @Test
    void shouldPreserveIdentityAndDisabledStateDuringContentEdits() {
        App original = App.create(new AppId("app"), "web", new AppAccess(Set.of()));
        App disabled = original.changeStatus(AppStatus.DISABLED);
        App edited = disabled.edit("ios", new AppAccess(Set.of("https://example.test")));
        assertEquals(original, edited);
        assertEquals(original.hashCode(), edited.hashCode());
        assertFalse(original.sameDefinition(edited));
        assertEquals(AppStatus.DISABLED, edited.getStatus());
        assertEquals("web", original.getPlatform());
        assertTrue(original.enabled());
    }

    @Test
    void shouldKeepCredentialsDuringAccessEditsAndAccessDuringRotation() {
        AppId appId = new AppId("app");
        Client original = Client.create(new ClientId("client"), Set.of(appId))
                .changeStatus(ClientStatus.DISABLED);
        Credentials credentials = new Credentials(credential("first"), null);
        Client rotated = original.rotateCredentials(credentials);
        Client edited = rotated.edit(Set.of());
        assertEquals(Set.of(appId), rotated.getAccess().allowedApps());
        assertSame(credentials, edited.getAccess().credentials());
        assertEquals(ClientStatus.DISABLED, edited.getStatus());
        assertEquals(original, edited);
        assertFalse(original.sameDefinition(edited));
        assertNull(original.getAccess().credentials());
    }

    @Test
    void shouldKeepIdentityEqualitySeparateFromEndpointAmbiguity() {
        Endpoint first = Endpoint.create(new EndpointId("endpoint"), new ServiceId("service"), new RouteId("route"),
                List.of(new EndpointMatcher("GET", "/first")), Set.of(),
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));
        Endpoint edited = first.changeStatus(EndpointStatus.DISABLED).edit(first.getServiceId(), first.getRouteId(),
                List.of(new EndpointMatcher("GET", "/second")), Set.of("tag"), first.getAccess());
        assertEquals(first, edited);
        assertEquals(EndpointStatus.DISABLED, edited.getStatus());
        assertInstanceOf(EndpointMatch.Ambiguous.class, EndpointMatch.of(List.of(first, edited)));
        assertInstanceOf(EndpointMatch.Unique.class, EndpointMatch.of(List.of(first,
                first.edit(first.getServiceId(), first.getRouteId(), first.getMatchers(), first.getTags(), first.getAccess()))));
    }

    @Test
    void shouldExposeCredentialBusinessCodesWithoutChangingNullPreconditions() {
        DomainException expiry = assertThrows(DomainException.class,
                () -> new Credential(new CredentialId("invalid"), "secret", SignatureContract.HTTP_REQUEST_SIGNATURE_V1,
                        Instant.EPOCH, Instant.EPOCH));
        assertEquals(DomainError.CLIENT_CREDENTIAL_INVALID.errorCode(), expiry.getErrorCode());
        Credential credential = credential("same");
        DomainException rotation = assertThrows(DomainException.class, () -> new Credentials(credential, credential));
        assertEquals(DomainError.CLIENT_CREDENTIAL_ROTATION_INVALID.errorCode(), rotation.getErrorCode());
        assertThrows(com.cloud.framework.core.error.FrameworkException.class, () -> new Credentials(null, null));
    }

    private static Credential credential(String id) {
        return new Credential(new CredentialId(id), "secret-reference", SignatureContract.HTTP_REQUEST_SIGNATURE_V1,
                Instant.EPOCH, Instant.parse("2099-01-01T00:00:00Z"));
    }
}

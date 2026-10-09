package com.cloud.gateway.domain.ingress.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IngressSnapshotTest {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-24T00:00:00Z");

    @Test
    void shouldClassifySnapshotAcceptanceByVersionAndContentHash() {
        IngressContent content = content(client("PARTNER", null));
        IngressSnapshot current = new IngressSnapshot(new SnapshotVersion(2), content, "current", PUBLISHED_AT);
        assertEquals(IngressSnapshot.Acceptance.STALE, current.acceptanceOf(
                new IngressSnapshot(new SnapshotVersion(1), content, "old", PUBLISHED_AT)));
        assertEquals(IngressSnapshot.Acceptance.UNCHANGED, current.acceptanceOf(
                new IngressSnapshot(new SnapshotVersion(2), content, "current", PUBLISHED_AT.plusSeconds(1))));
        assertEquals(IngressSnapshot.Acceptance.CONFLICT, current.acceptanceOf(
                new IngressSnapshot(new SnapshotVersion(2), content, "different", PUBLISHED_AT)));
        assertEquals(IngressSnapshot.Acceptance.ADVANCE, current.acceptanceOf(
                new IngressSnapshot(new SnapshotVersion(3), content, "current", PUBLISHED_AT)));
    }

    @Test
    void shouldRejectSnapshotWhoseActiveCredentialIsNotEffectiveAtPublication() {
        Credential future = credential("active", PUBLISHED_AT.plusSeconds(60), PUBLISHED_AT.plusSeconds(3600));

        DomainException exception = assertThrows(DomainException.class,
                () -> snapshot(content(client("PARTNER", new Credentials(future, null)))));

        assertEquals(DomainError.ACCESS_SNAPSHOT_INVALID.getErrorCode(), exception.getErrorCode());
    }

    @Test
    void shouldRejectSnapshotWhoseGraceCredentialIsNotEffectiveAtPublication() {
        Credential active = credential("active", Instant.EPOCH, PUBLISHED_AT.plusSeconds(3600));
        Credential expiredGrace = credential("grace", Instant.EPOCH, PUBLISHED_AT.minusSeconds(1));

        DomainException exception = assertThrows(DomainException.class,
                () -> snapshot(content(client("PARTNER", new Credentials(active, expiredGrace)))));

        assertEquals(DomainError.ACCESS_SNAPSHOT_INVALID.getErrorCode(), exception.getErrorCode());
    }

    @Test
    void shouldRejectDuplicateCredentialIdsAcrossClients() {
        Credentials shared = new Credentials(
                credential("shared", Instant.EPOCH, PUBLISHED_AT.plusSeconds(3600)), null);

        DomainException exception = assertThrows(DomainException.class,
                () -> snapshot(content(client("PARTNER", shared), client("OTHER", shared))));

        assertEquals(DomainError.ACCESS_SNAPSHOT_INVALID.getErrorCode(), exception.getErrorCode());
    }

    @Test
    void shouldRejectClientReferencingUnknownApp() {
        Client unknownAppClient = new Client(new ClientId("PARTNER"), ClientStatus.ENABLED,
                new ClientAccess(Set.of(new AppId("unknown")), credentials()));

        DomainException exception = assertThrows(DomainException.class,
                () -> snapshot(content(unknownAppClient)));

        assertEquals(DomainError.ACCESS_SNAPSHOT_INVALID.getErrorCode(), exception.getErrorCode());
    }

    @Test
    void shouldAcceptSnapshotWithoutClientCredentials() {
        IngressSnapshot snapshot = snapshot(content(client("PARTNER", null)));

        assertEquals("hash", snapshot.contentHash());
        assertEquals(1, snapshot.version().value());
    }

    private static Credential credential(String id, Instant from, Instant to) {
        return new Credential(new CredentialId(id), "secret-ref",
                SignatureContract.LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256, from, to);
    }

    private static Credentials credentials() {
        return new Credentials(credential("active", Instant.EPOCH, PUBLISHED_AT.plusSeconds(3600)), null);
    }

    private static Client client(String name, Credentials credentials) {
        return new Client(new ClientId(name), ClientStatus.ENABLED,
                new ClientAccess(Set.of(new AppId("app")), credentials));
    }

    private static IngressContent content(Client... clients) {
        return new IngressContent(
                new AppRegistry(List.of(new App(new AppId("app"), "APP", AppStatus.ENABLED, new AppAccess(Set.of())))),
                new ClientRegistry(List.of(clients)),
                new EndpointRegistry(List.of(endpoint())),
                CapabilityPolicyConfig.empty());
    }

    private static IngressSnapshot snapshot(IngressContent content) {
        return new IngressSnapshot(new SnapshotVersion(1), content, "hash", PUBLISHED_AT);
    }

    private static Endpoint endpoint() {
        return new Endpoint(new EndpointId("LOGIN"), new ServiceId("service"), new RouteId("route"),
                List.of(new EndpointMatcher("POST", "/login")), Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.REQUIRED,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));
    }
}

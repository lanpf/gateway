package com.cloud.gateway.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.client.SignatureContract;
import org.junit.jupiter.api.Test;

class IngressAccessSnapshotTest {
    @Test
    void shouldClassifyStaleIdenticalConflictingAndAdvancedSnapshots() {
        Instant time = Instant.parse("2026-08-24T00:00:00Z");
        IngressAccessContent content = new IngressAccessContent(catalog(), Map.of(), Map.of(), Map.of());
        IngressAccessSnapshot current = new IngressAccessSnapshot(2, content, "current", time);
        assertEquals(IngressAccessSnapshot.Admission.STALE,
                current.admissionOf(new IngressAccessSnapshot(1, content, "older", time)));
        assertEquals(IngressAccessSnapshot.Admission.UNCHANGED,
                current.admissionOf(new IngressAccessSnapshot(2, content, "current", time.plusSeconds(1))));
        assertEquals(IngressAccessSnapshot.Admission.CONFLICT,
                current.admissionOf(new IngressAccessSnapshot(2, content, "different", time)));
        assertEquals(IngressAccessSnapshot.Admission.ADVANCE,
                current.admissionOf(new IngressAccessSnapshot(3, content, "current", time)));
    }

    @Test
    void shouldAcceptPlatformCorrectionWhenAdmittingNewerSnapshot() {
        AppId id = new AppId("app");
        IngressAccessContent oldContent = new IngressAccessContent(catalog(),
                Map.of(id, new AppPolicy(id, "WEB", Set.of(), PolicyStatus.ENABLED)), Map.of(), Map.of());
        IngressAccessContent newContent = new IngressAccessContent(catalog(),
                Map.of(id, new AppPolicy(id, "IOS", Set.of(), PolicyStatus.ENABLED)), Map.of(), Map.of());
        IngressAccessSnapshot current = new IngressAccessSnapshot(1, oldContent, "old", Instant.EPOCH);
        IngressAccessSnapshot candidate = new IngressAccessSnapshot(2, newContent, "new", Instant.EPOCH);
        assertEquals(IngressAccessSnapshot.Admission.ADVANCE, current.admissionOf(candidate));
        assertEquals("IOS", candidate.content().appPolicies().get(id).platform());
        assertEquals("WEB", current.content().appPolicies().get(id).platform());
    }

    @Test
    void shouldRejectSnapshotWhoseActiveCredentialIsNotEffectiveAtPublication() {
        Instant publishedAt = Instant.parse("2026-08-24T00:00:00Z");
        Credential future = new Credential(
                new CredentialId("client-1"), new ClientId("PARTNER"),
                SignatureContract.LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256, "partner",
                publishedAt.plusSeconds(60), publishedAt.plusSeconds(3600));
        IngressAccessContent content = new IngressAccessContent(catalog(),
                Map.of(new AppId("app"), new AppPolicy(
                        new AppId("app"), "APP", Set.of(), PolicyStatus.ENABLED)),
                Map.of(new ClientId("PARTNER"), new ClientPolicy(new ClientId("PARTNER"), Set.of(new AppId("app")), new Credentials(future, null), ClientStatus.ENABLED)),
                Map.of(new EndpointId("LOGIN"), route()));

        DomainException exception = assertThrows(DomainException.class,
                () -> new IngressAccessSnapshot(1, content, "hash", publishedAt));

        assertEquals(DomainError.ACCESS_SNAPSHOT_INVALID.getErrorCode(), exception.getErrorCode());
    }

    private static EndpointRegistry catalog() {
        return new EndpointRegistry(List.of(new Endpoint(new EndpointId("LOGIN"), "service",
                List.of(new EndpointMatcher("POST", "/login")), "service")));
    }
    private static EndpointPolicy route() {
        return new EndpointPolicy(new EndpointId("LOGIN"), ClientVerificationRequirement.REQUIRED, ClientVerificationMode.IGNORE, SubjectAuthenticationRequirement.IGNORE, Set.of(), Set.of(), CredentialForwarding.stripAll(), EndpointStatus.ENABLED);
    }
}

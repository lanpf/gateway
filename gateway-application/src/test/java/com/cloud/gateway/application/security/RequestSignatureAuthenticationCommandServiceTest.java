package com.cloud.gateway.application.security;

import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.ingress.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.security.signature.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestSignatureAuthenticationCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private final SignatureCryptography cryptography = new StubCryptography();

    @Test
    void shouldNotTryGraceOrRegisterWhenSecretDependencyFails() {
        AtomicInteger resolutions = new AtomicInteger();
        RequestSignatureAuthenticationCommandService service = service(ref -> {
            resolutions.incrementAndGet();
            throw new IllegalStateException("offline");
        }, (key, expiry) -> { throw new AssertionError("must not register"); });
        assertInstanceOf(ClientVerificationOutcome.Failed.class,
                service.authenticate(credentials(), new ClientId("client"), evidence(), NOW).toCompletableFuture().join());
        assertEquals(1, resolutions.get());
    }

    @Test
    void shouldEstablishIdentityOnlyAfterSuccessfulRegistration() {
        CompletableFuture<Boolean> registration = new CompletableFuture<>();
        RequestSignatureAuthenticationCommandService service = service(ref -> "secret", (key, expiry) -> registration);
        CompletableFuture<ClientVerificationOutcome> authentication =
                service.authenticate(credentials(), new ClientId("client"), evidence(), NOW).toCompletableFuture();
        assertFalse(authentication.isDone());
        registration.complete(true);
        assertEquals("active", assertInstanceOf(ClientVerificationOutcome.Verified.class,
                authentication.join()).client().credentialId().value());
    }

    @Test
    void shouldCancelPendingReplayWhenAuthenticationIsCancelled() {
        CompletableFuture<Boolean> registration = new CompletableFuture<>();
        RequestSignatureAuthenticationCommandService service = service(ref -> "secret", (key, expiry) -> registration);
        CompletableFuture<ClientVerificationOutcome> authentication =
                service.authenticate(credentials(), new ClientId("client"), evidence(), NOW).toCompletableFuture();
        authentication.cancel(true);
        assertTrue(registration.isCancelled());
        assertTrue(authentication.isCancelled());
    }

    @Test
    void shouldPropagateReplayCancellationWithoutInventingUnavailableOutcome() {
        CompletableFuture<Boolean> registration = new CompletableFuture<>();
        RequestSignatureAuthenticationCommandService service = service(ref -> "secret", (key, expiry) -> registration);
        CompletableFuture<ClientVerificationOutcome> authentication =
                service.authenticate(credentials(), new ClientId("client"), evidence(), NOW).toCompletableFuture();
        registration.cancel(false);
        assertTrue(authentication.isCancelled());
    }

    @Test
    void shouldDistinguishDuplicateRegistrationFromDependencyFailureAndNullReply() {
        assertInstanceOf(ClientVerificationOutcome.Rejected.class, service(ref -> "secret",
                (key, expiry) -> CompletableFuture.completedFuture(false)).authenticate(credentials(), new ClientId("client"), evidence(), NOW)
                .toCompletableFuture().join());
        assertInstanceOf(ClientVerificationOutcome.Failed.class, service(ref -> "secret",
                (key, expiry) -> CompletableFuture.failedFuture(new IllegalStateException("missing reply")))
                .authenticate(credentials(), new ClientId("client"), evidence(), NOW).toCompletableFuture().join());
        assertInstanceOf(ClientVerificationOutcome.Failed.class, service(ref -> "secret",
                (key, expiry) -> CompletableFuture.completedFuture(null)).authenticate(credentials(), new ClientId("client"), evidence(), NOW)
                .toCompletableFuture().join());
    }

    private RequestSignatureAuthenticationCommandService service(SecretResolver secrets, ReplayGuard replay) {
        RequestSignatureVerifierRegistry verifiers = new RequestSignatureVerifierRegistry(List.of(
                new LegacySecretParamsTimestampMd5UpperSignatureVerifier(cryptography),
                new LegacySecretTimestampBodySha256SignatureVerifier(cryptography),
                new SecretTimestampBodyHmacSha256SignatureVerifier(cryptography),
                new HttpRequestSignatureV1Verifier(cryptography)));
        return new RequestSignatureAuthenticationCommandService(secrets, replay, verifiers, cryptography,
                new SignatureAuthenticationRules(Duration.ofMinutes(5)));
    }

    private static Credentials credentials() {
        return new Credentials(credential("active"), credential("grace"));
    }

    private static Credential credential(String id) {
        return new Credential(new CredentialId(id), id,
                SignatureContract.LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256,
                NOW.minusSeconds(60), NOW.plusSeconds(600));
    }

    private static ClientSignatureEvidence evidence() {
        return new ClientSignatureEvidence(new byte[0], Long.toString(NOW.toEpochMilli()), "signature");
    }

    private static final class StubCryptography implements SignatureCryptography {
        @Override public String digest(Digest algorithm, byte[] input) { return "digest"; }
        @Override public String hmacSha256(String secret, byte[] input) { return "signature"; }
        @Override public boolean matches(String expected, String actual) { return expected.equals(actual); }
    }
}

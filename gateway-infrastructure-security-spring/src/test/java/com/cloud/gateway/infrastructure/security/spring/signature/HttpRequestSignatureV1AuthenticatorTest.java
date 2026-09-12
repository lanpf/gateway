package com.cloud.gateway.infrastructure.security.spring.signature;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.application.security.RequestSignatureAuthenticationCommandService;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.security.signature.*;
import com.cloud.gateway.application.security.ReplayGuard;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.SignatureContract;
import org.junit.jupiter.api.Test;

class HttpRequestSignatureV1AuthenticatorTest {
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final Duration SKEW = Duration.ofMinutes(5);
    private static final String NONCE = "abcdefghijklmnopqrstuv";

    @Test
    void shouldSelectExactActiveAndGraceCredentialWithoutFallback() {
        AtomicInteger resolutions = new AtomicInteger();
        RequestSignatureAuthenticationCommandService authenticator = new RequestSignatureAuthenticationCommandService(ref -> {
            resolutions.incrementAndGet();
            return ref;
        }, (key, expiry) -> CompletableFuture.completedFuture(true), registry(), new RequestSignatureCryptography(), new SignatureAuthenticationRules(SKEW));
        Credentials policy = policy();
        ClientVerificationOutcome active = authenticator.authenticate(policy, evidence("active", "active", NOW, NONCE), NOW)
                .toCompletableFuture().join();
        assertEquals("active", assertInstanceOf(ClientVerificationOutcome.Verified.class, active)
                .client().credentialId().value());
        ClientVerificationOutcome grace = authenticator.authenticate(policy, evidence("grace", "grace", NOW, NONCE), NOW)
                .toCompletableFuture().join();
        assertEquals("grace", assertInstanceOf(ClientVerificationOutcome.Verified.class, grace)
                .client().credentialId().value());
        assertEquals(2, resolutions.get());
        assertRejected(authenticator, policy, evidence("unknown", "active", NOW, NONCE), NOW);
        assertEquals(2, resolutions.get());
        assertRejected(authenticator, policy, evidence("active", "grace", NOW, NONCE), NOW);
        assertEquals(3, resolutions.get());
    }

    @Test
    void shouldRejectNonceReuseAcrossCredentialsRequestsAndConcurrentInstances() {
        Set<String> keys = ConcurrentHashMap.newKeySet();
        ReplayGuard guard = (key, expiry) -> CompletableFuture.completedFuture(keys.add(key));
        RequestSignatureAuthenticationCommandService first = authenticator(guard);
        RequestSignatureAuthenticationCommandService second = authenticator(guard);
        List<CompletableFuture<ClientVerificationOutcome>> outcomes = IntStream.range(0, 32).mapToObj(index -> CompletableFuture.supplyAsync(() ->
                (index % 2 == 0 ? first : second).authenticate(policy(),
                        evidence(index % 2 == 0 ? "active" : "grace",
                                index % 2 == 0 ? "active" : "grace", NOW.plusMillis(index), NONCE), NOW)
                        .toCompletableFuture().join())).toList();
        long successes = outcomes.stream().map(CompletableFuture::join)
                .filter(ClientVerificationOutcome.Verified.class::isInstance).count();
        assertEquals(1, successes);
        assertEquals(1, keys.size());
    }

    @Test
    void shouldRetainReplayMarkerThroughFutureTimestampAcceptanceBoundary() {
        AtomicReference<Instant> expiry = new AtomicReference<Instant>();
        RequestSignatureAuthenticationCommandService authenticator = authenticator((key, deadline) -> {
            expiry.set(deadline);
            return CompletableFuture.completedFuture(true);
        });
        Instant future = NOW.plus(SKEW);
        authenticator.authenticate(policy(), evidence("active", "active", future, NONCE), NOW)
                .toCompletableFuture().join();
        assertEquals(future.plus(SKEW).plusMillis(1), expiry.get());
        assertRejected(authenticator, policy(), evidence("active", "active", future.plusMillis(1), NONCE), NOW);
        assertRejected(authenticator, policy(), evidence("active", "active", NOW.minus(SKEW).minusMillis(1), NONCE), NOW);
    }

    @Test
    void shouldRejectExpiredCredentialsAndClientMismatchBeforeReplay() {
        AtomicInteger registrations = new AtomicInteger();
        RequestSignatureAuthenticationCommandService authenticator = authenticator((key, expiry) -> {
            registrations.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        });
        Instant expired = NOW.plusSeconds(600);
        assertRejected(authenticator, policy(), evidence("active", "active", expired, NONCE), expired);
        ClientSignatureEvidence original = evidence("active", "active", NOW, NONCE);
        ClientSignatureEvidence mismatch = new ClientSignatureEvidence(original.payload(), original.timestamp(), original.signature(),
                original.method(), original.rawPath(), original.rawQuery(), original.appId(), "other-client",
                original.credentialId(), original.nonce());
        assertRejected(authenticator, policy(), mismatch, NOW);
        assertEquals(0, registrations.get());
    }

    @Test
    void shouldNotAuthenticateWhenReplayDependencyFailsOrRefusesRegistration() {
        RequestSignatureAuthenticationCommandService unavailable = authenticator((key, expiry) -> CompletableFuture.failedFuture(new IllegalStateException("offline")));
        assertInstanceOf(ClientVerificationOutcome.Failed.class, unavailable.authenticate(policy(),
                evidence("active", "active", NOW, NONCE), NOW).toCompletableFuture().join());
        assertRejected(authenticator((key, expiry) -> CompletableFuture.completedFuture(false)),
                policy(), evidence("active", "active", NOW, NONCE), NOW);
    }

    @Test
    void shouldReportSynchronousReplayFailureAsUnavailable() {
        RequestSignatureAuthenticationCommandService unavailable = authenticator((key, expiry) -> { throw new IllegalStateException("offline"); });
        assertInstanceOf(ClientVerificationOutcome.Failed.class, unavailable.authenticate(policy(),
                evidence("active", "active", NOW, NONCE), NOW).toCompletableFuture().join());
    }

    @Test
    void shouldReportSecretDependencyFailureWithoutFallingBackOrRegisteringReplay() {
        AtomicInteger resolutions = new AtomicInteger();
        RequestSignatureAuthenticationCommandService unavailable = new RequestSignatureAuthenticationCommandService(ref -> {
            resolutions.incrementAndGet();
            throw new IllegalStateException("secret service unavailable");
        }, (key, expiry) -> {
            fail("must not register unverified evidence");
            return CompletableFuture.completedFuture(true);
        }, registry(), new RequestSignatureCryptography(), new SignatureAuthenticationRules(SKEW));
        assertInstanceOf(ClientVerificationOutcome.Failed.class, unavailable.authenticate(policy(),
                evidence("active", "active", NOW, NONCE), NOW).toCompletableFuture().join());
        assertEquals(1, resolutions.get());
        assertRejected(unavailable, policy(), new ClientSignatureEvidence(new byte[0],
                Long.toString(NOW.toEpochMilli()), "malformed"), NOW);
        assertEquals(1, resolutions.get(), "malformed v1 must reject without querying unavailable dependency");
    }

    @Test
    void shouldKeepInvalidSignatureRejectedWhenReplayDependencyIsUnavailable() {
        RequestSignatureAuthenticationCommandService unavailable = authenticator((key, expiry) -> { throw new AssertionError("invalid signature must not reach replay"); });
        assertRejected(unavailable, policy(), evidence("active", "wrong-secret", NOW, NONCE), NOW);
    }

    @Test
    void shouldTreatMissingSecretOrNullRegistrationAsUnavailable() {
        for (String secret : new String[]{null, "", " "}) {
            RequestSignatureAuthenticationCommandService unavailable = new RequestSignatureAuthenticationCommandService(ref -> secret,
                    (key, expiry) -> { throw new AssertionError("must not reach replay"); }, registry(), new RequestSignatureCryptography(), new SignatureAuthenticationRules(SKEW));
            assertInstanceOf(ClientVerificationOutcome.Failed.class, unavailable.authenticate(policy(),
                    evidence("active", "active", NOW, NONCE), NOW).toCompletableFuture().join());
        }
        assertInstanceOf(ClientVerificationOutcome.Failed.class,
                authenticator((key, expiry) -> CompletableFuture.completedFuture(null)).authenticate(policy(),
                        evidence("active", "active", NOW, NONCE), NOW).toCompletableFuture().join());
    }

    @Test
    void shouldNotMisclassifyVerifierProgrammingFailureAsDependencyUnavailable() {
        RequestSignatureVerifier brokenVerifier = new RequestSignatureVerifier() {
            @Override public SignatureContract contract() {
                return SignatureContract.HTTP_REQUEST_SIGNATURE_V1;
            }
            @Override public boolean verify(String secret, SignatureInput evidence) {
                throw new IllegalStateException("verifier implementation failure");
            }
        };
        RequestSignatureVerifierRegistry brokenRegistry = new RequestSignatureVerifierRegistry(List.of(
                new LegacySecretParamsTimestampMd5UpperSignatureVerifier(new RequestSignatureCryptography()),
                new LegacySecretTimestampBodySha256SignatureVerifier(new RequestSignatureCryptography()),
                new SecretTimestampBodyHmacSha256SignatureVerifier(new RequestSignatureCryptography()), brokenVerifier));
        RequestSignatureAuthenticationCommandService authenticator = new RequestSignatureAuthenticationCommandService(ref -> ref,
                (key, expiry) -> { throw new AssertionError("must not reach replay"); }, brokenRegistry, new RequestSignatureCryptography(), new SignatureAuthenticationRules(SKEW));
        assertThrows(IllegalStateException.class, () -> authenticator.authenticate(policy(),
                evidence("active", "active", NOW, NONCE), NOW));
    }

    @Test
    void shouldNeverTryLegacyProtocolOnVersionOneFailure() {
        ClientSignatureEvidence legacy = new ClientSignatureEvidence("payload".getBytes(StandardCharsets.UTF_8),
                Long.toString(NOW.toEpochMilli()), new RequestSignatureCryptography().hmacSha256("active",
                        (Long.toString(NOW.toEpochMilli()) + "payload").getBytes(StandardCharsets.UTF_8)));
        assertRejected(authenticator((key, expiry) -> CompletableFuture.completedFuture(true)), policy(), legacy, NOW);
    }

    private static void assertRejected(RequestSignatureAuthenticationCommandService authenticator,
                                       Credentials policy, ClientSignatureEvidence evidence, Instant now) {
        assertInstanceOf(ClientVerificationOutcome.Rejected.class,
                authenticator.authenticate(policy, evidence, now).toCompletableFuture().join());
    }

    private static RequestSignatureAuthenticationCommandService authenticator(ReplayGuard guard) {
        return new RequestSignatureAuthenticationCommandService(ref -> ref, guard, registry(), new RequestSignatureCryptography(), new SignatureAuthenticationRules(SKEW));
    }

    private static Credentials policy() {
        return new Credentials(credential("active"), credential("grace"));
    }

    private static Credential credential(String id) {
        return new Credential(new CredentialId(id), new ClientId("client-a"),
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, id,
                NOW.minusSeconds(600), NOW.plusSeconds(600));
    }

    private static ClientSignatureEvidence evidence(String id, String secret, Instant timestamp, String nonce) {
        ClientSignatureEvidence unsigned = new ClientSignatureEvidence("payload".getBytes(StandardCharsets.UTF_8),
                Long.toString(timestamp.toEpochMilli()), "unsigned", "POST", "/orders", "a=1", "app-a", "client-a", id, nonce);
        String signature = new RequestSignatureCryptography().hmacSha256(secret, HttpRequestSignatureV1Verifier.canonicalPayload(unsigned, RequestSignatureCryptography.digest(RequestSignatureCryptography.SHA_256, unsigned.payload())));
        return new ClientSignatureEvidence(unsigned.payload(), unsigned.timestamp(), signature, unsigned.method(),
                unsigned.rawPath(), unsigned.rawQuery(), unsigned.appId(), unsigned.clientId(), id, nonce);
    }

    private static RequestSignatureVerifierRegistry registry() {
        return new RequestSignatureVerifierRegistry(List.of(new LegacySecretParamsTimestampMd5UpperSignatureVerifier(new RequestSignatureCryptography()),
                new LegacySecretTimestampBodySha256SignatureVerifier(new RequestSignatureCryptography()), new SecretTimestampBodyHmacSha256SignatureVerifier(new RequestSignatureCryptography()),
                new HttpRequestSignatureV1Verifier(new RequestSignatureCryptography())));
    }
}

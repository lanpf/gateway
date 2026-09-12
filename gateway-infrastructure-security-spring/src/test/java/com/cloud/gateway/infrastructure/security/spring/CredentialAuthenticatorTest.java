package com.cloud.gateway.infrastructure.security.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.application.security.RequestSignatureAuthenticationCommandService;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.security.signature.*;
import com.cloud.gateway.application.security.ReplayGuard;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.SignatureContract;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.cloud.gateway.domain.security.signature.LegacySecretParamsTimestampMd5UpperSignatureVerifier;
import com.cloud.gateway.domain.security.signature.LegacySecretTimestampBodySha256SignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureCryptography;
import com.cloud.gateway.domain.security.signature.RequestSignatureVerifierRegistry;
import com.cloud.gateway.domain.security.signature.SecretTimestampBodyHmacSha256SignatureVerifier;
import com.cloud.gateway.domain.security.signature.HttpRequestSignatureV1Verifier;
import org.junit.jupiter.api.Test;

class CredentialAuthenticatorTest {
    @Test
    void shouldUseGraceCredentialAtMostOnceDuringRotation() throws Exception {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        Credential active = credential("active", "active-secret", now);
        Credential grace = credential("grace", "grace-secret", now);
        Set<String> registered = new HashSet<>();
        ReplayGuard replayGuard = (key, expiresAt) -> CompletableFuture.completedFuture(registered.add(key));
        RequestSignatureAuthenticationCommandService authenticator = new RequestSignatureAuthenticationCommandService(
                ref -> ref.equals("active-secret") ? "new-value" : "old-value",
                replayGuard, verifierRegistry(), new RequestSignatureCryptography(), new SignatureAuthenticationRules(Duration.ofMinutes(5)));
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(now.toEpochMilli());
        String signature = hmac("old-value", timestamp, body);
        ClientSignatureEvidence evidence = new ClientSignatureEvidence(body, timestamp, signature);

        ClientVerificationOutcome first = authenticator.authenticate(
                new Credentials(active, grace), evidence, now)
                .toCompletableFuture().join();
        ClientVerificationOutcome replay = authenticator.authenticate(
                new Credentials(active, grace), evidence, now)
                .toCompletableFuture().join();
        ClientVerificationOutcome caseReplay = authenticator.authenticate(
                new Credentials(active, grace),
                new ClientSignatureEvidence(body, timestamp, signature.toUpperCase(java.util.Locale.ROOT)), now)
                .toCompletableFuture().join();

        VerifiedClient verified = assertInstanceOf(
                ClientVerificationOutcome.Verified.class, first).client();
        assertEquals("grace", verified.credentialId().value());
        assertInstanceOf(ClientVerificationOutcome.Rejected.class, replay);
        assertInstanceOf(ClientVerificationOutcome.Rejected.class, caseReplay);
    }

    @Test
    void shouldRejectMalformedTimestamp() {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        Credential credential = credential("active", "active-secret", now);
        RequestSignatureAuthenticationCommandService authenticator = new RequestSignatureAuthenticationCommandService(
                ignored -> "secret",
                (key, expiresAt) -> CompletableFuture.completedFuture(true),
                verifierRegistry(), new RequestSignatureCryptography(),
                new SignatureAuthenticationRules(Duration.ofMinutes(5)));
        ClientSignatureEvidence evidence = new ClientSignatureEvidence(new byte[0], "not-a-timestamp", "signature");

        ClientVerificationOutcome result = authenticator.authenticate(
                        new Credentials(credential, null), evidence, now)
                .toCompletableFuture()
                .join();

        assertInstanceOf(ClientVerificationOutcome.Rejected.class, result);
    }

    private static Credential credential(String id, String ref, Instant now) {
        return new Credential(new CredentialId(id), new ClientId("PARTNER"),
                SignatureContract.LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256, ref,
                now.minusSeconds(60), now.plusSeconds(600));
    }

    private static String hmac(String secret, String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance(RequestSignatureCryptography.HMAC_SHA_256);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
                RequestSignatureCryptography.HMAC_SHA_256));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static RequestSignatureVerifierRegistry verifierRegistry() {
        return new RequestSignatureVerifierRegistry(List.of(
                new LegacySecretParamsTimestampMd5UpperSignatureVerifier(new RequestSignatureCryptography()),
                new LegacySecretTimestampBodySha256SignatureVerifier(new RequestSignatureCryptography()),
                new SecretTimestampBodyHmacSha256SignatureVerifier(new RequestSignatureCryptography()),
                new HttpRequestSignatureV1Verifier(new RequestSignatureCryptography())));
    }
}

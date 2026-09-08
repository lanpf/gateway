package com.cloud.gateway.infrastructure.security.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cloud.gateway.application.security.ChannelSignatureEvidence;
import com.cloud.gateway.application.security.ReplayGuard;
import com.cloud.gateway.domain.access.ChannelCredentialAuthenticationOutcome;
import com.cloud.gateway.domain.access.VerifiedChannelCredential;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ChannelCredentialId;
import com.cloud.gateway.domain.policy.RequestSignatureChannelCredential;
import com.cloud.gateway.domain.policy.SignaturePayloadContract;
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

import com.cloud.gateway.infrastructure.security.spring.signature.LegacySecretParamsTimestampMd5UpperSignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.LegacySecretTimestampBodySha256SignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureChannelCredentialAuthenticator;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureCryptography;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureVerifierRegistry;
import com.cloud.gateway.infrastructure.security.spring.signature.SecretTimestampBodyHmacSha256SignatureVerifier;
import org.junit.jupiter.api.Test;

class RequestSignatureChannelCredentialAuthenticatorTest {
    @Test
    void shouldUseGraceCredentialAtMostOnceDuringRotation() throws Exception {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        RequestSignatureChannelCredential active = credential("active", "active-secret", now);
        RequestSignatureChannelCredential grace = credential("grace", "grace-secret", now);
        Set<String> registered = new HashSet<>();
        ReplayGuard replayGuard = (key, expiresAt) -> CompletableFuture.completedFuture(registered.add(key));
        RequestSignatureChannelCredentialAuthenticator authenticator = new RequestSignatureChannelCredentialAuthenticator(
                ref -> ref.equals("active-secret") ? "new-value" : "old-value",
                replayGuard, verifierRegistry(), Duration.ofMinutes(5));
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(now.toEpochMilli());
        String signature = hmac("old-value", timestamp, body);
        ChannelSignatureEvidence evidence = new ChannelSignatureEvidence(body, timestamp, signature);

        ChannelCredentialAuthenticationOutcome first = authenticator.authenticate(
                new IngressChannelCredentialPolicy(new ChannelCode("PARTNER"), active, grace), evidence, now)
                .toCompletableFuture().join();
        ChannelCredentialAuthenticationOutcome replay = authenticator.authenticate(
                new IngressChannelCredentialPolicy(new ChannelCode("PARTNER"), active, grace), evidence, now)
                .toCompletableFuture().join();

        VerifiedChannelCredential verified = assertInstanceOf(
                ChannelCredentialAuthenticationOutcome.Authenticated.class, first).credential();
        assertEquals("grace", verified.credentialId().value());
        assertInstanceOf(ChannelCredentialAuthenticationOutcome.Rejected.class, replay);
    }

    @Test
    void shouldRejectMalformedTimestamp() {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        RequestSignatureChannelCredential credential = credential("active", "active-secret", now);
        RequestSignatureChannelCredentialAuthenticator authenticator = new RequestSignatureChannelCredentialAuthenticator(
                ignored -> "secret",
                (key, expiresAt) -> CompletableFuture.completedFuture(true),
                verifierRegistry(),
                Duration.ofMinutes(5));
        ChannelSignatureEvidence evidence = new ChannelSignatureEvidence(new byte[0], "not-a-timestamp", "signature");

        ChannelCredentialAuthenticationOutcome result = authenticator.authenticate(
                        new IngressChannelCredentialPolicy(new ChannelCode("PARTNER"), credential, null), evidence, now)
                .toCompletableFuture()
                .join();

        assertInstanceOf(ChannelCredentialAuthenticationOutcome.Rejected.class, result);
    }

    private static RequestSignatureChannelCredential credential(String id, String ref, Instant now) {
        return new RequestSignatureChannelCredential(new ChannelCredentialId(id), now.minusSeconds(60),
                now.plusSeconds(600), new ChannelCode("PARTNER"),
                SignaturePayloadContract.SECRET_TIMESTAMP_BODY_HMAC_SHA256, ref);
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
                new LegacySecretParamsTimestampMd5UpperSignatureVerifier(),
                new LegacySecretTimestampBodySha256SignatureVerifier(),
                new SecretTimestampBodyHmacSha256SignatureVerifier()));
    }
}

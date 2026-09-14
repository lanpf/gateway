package com.cloud.gateway.domain.security.signature;

import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.SignatureContract;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignatureAuthenticationRulesTest {
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final Duration SKEW = Duration.ofMinutes(5);
    private static final String NONCE = "abcdefghijklmnopqrstuv";
    private static final ClientId CLIENT = new ClientId("client-a");
    private final SignatureAuthenticationRules rules = new SignatureAuthenticationRules(SKEW);

    @Test
    void shouldSelectOnlyExactValidVersionOneCredential() {
        Credentials credentials = credentials(SignatureContract.HTTP_REQUEST_SIGNATURE_V1);
        assertEquals(List.of(credentials.activeCredential()), rules.candidates(CLIENT, credentials, evidence("active", NOW), NOW));
        assertEquals(List.of(credentials.graceCredential()), rules.candidates(CLIENT, credentials, evidence("grace", NOW), NOW));
        assertTrue(rules.candidates(CLIENT, credentials, evidence("unknown", NOW), NOW).isEmpty());
        assertTrue(rules.candidates(CLIENT, credentials, evidence("active", NOW), NOW.plusSeconds(1000)).isEmpty());
    }

    @Test
    void shouldKeepLegacyCandidateOrderAndRejectExpiredCandidates() {
        Credentials credentials = credentials(SignatureContract.LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256);
        assertEquals(List.of(credentials.activeCredential(), credentials.graceCredential()),
                rules.candidates(CLIENT, credentials, evidence("ignored", NOW), NOW));
        assertTrue(rules.candidates(CLIENT, credentials, evidence("ignored", NOW), NOW.plusSeconds(1000)).isEmpty());
    }

    @Test
    void shouldAcceptInclusiveTimeWindowAndRetainMarkerBeyondFutureBoundary() {
        assertEquals(NOW.plus(SKEW), rules.acceptedTimestamp(evidence("active", NOW.plus(SKEW)), NOW).orElseThrow());
        assertTrue(rules.acceptedTimestamp(evidence("active", NOW.plus(SKEW).plusMillis(1)), NOW).isEmpty());
        assertTrue(rules.acceptedTimestamp(evidence("active", NOW.minus(SKEW).minusMillis(1)), NOW).isEmpty());
        assertEquals(NOW.plus(SKEW).plus(SKEW).plusMillis(1), rules.replayExpiresAt(NOW.plus(SKEW)));
    }

    @Test
    void shouldShareReplayIdentityAcrossCredentialRotation() {
        Credentials credentials = credentials(SignatureContract.HTTP_REQUEST_SIGNATURE_V1);
        assertEquals("HTTP_REQUEST_SIGNATURE_V1:8:client-a:" + NONCE, rules.replayIdentity(CLIENT, credentials, evidence("active", NOW)));
        assertEquals(rules.replayIdentity(CLIENT, credentials, evidence("active", NOW)),
                rules.replayIdentity(CLIENT, credentials, evidence("grace", NOW.plusSeconds(1))));
    }

    @Test
    void shouldKeepCanonicalFieldOrderAndAbsentQueryDistinct() {
        Evidence absent = evidence("active", NOW);
        String payload = new String(HttpRequestSignatureV1Verifier.canonicalPayload(absent, "digest"), StandardCharsets.UTF_8);
        assertTrue(payload.startsWith("25:HTTP_REQUEST_SIGNATURE_V1,4:POST,7:/orders,-1:,"));
        assertTrue(payload.endsWith("6:digest,"));
        Evidence empty = new Evidence(absent.timestamp(), absent.signature(), absent.credentialId(), "");
        assertNotEquals(payload, new String(HttpRequestSignatureV1Verifier.canonicalPayload(empty, "digest"), StandardCharsets.UTF_8));
    }

    private static Credentials credentials(SignatureContract contract) {
        return new Credentials(credential("active", contract), credential("grace", contract));
    }

    private static Credential credential(String id, SignatureContract contract) {
        return new Credential(new CredentialId(id), id, contract,
                NOW.minusSeconds(600), NOW.plusSeconds(600));
    }

    private static Evidence evidence(String id, Instant timestamp) {
        return new Evidence(Long.toString(timestamp.toEpochMilli()), "a".repeat(64), id, null);
    }

    private record Evidence(String timestamp, String signature, String credentialId, String rawQuery) implements SignatureInput {
        @Override public byte[] payload() { return new byte[0]; }
        @Override public String method() { return "POST"; }
        @Override public String rawPath() { return "/orders"; }
        @Override public String appId() { return "app-a"; }
        @Override public String clientId() { return "client-a"; }
        @Override public String nonce() { return NONCE; }
    }
}

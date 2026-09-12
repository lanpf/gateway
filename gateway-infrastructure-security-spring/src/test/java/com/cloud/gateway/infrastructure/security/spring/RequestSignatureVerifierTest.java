package com.cloud.gateway.infrastructure.security.spring;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.application.security.RequestSignatureAuthenticationCommandService;
import com.cloud.gateway.domain.security.signature.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.cloud.gateway.domain.security.signature.LegacySecretParamsTimestampMd5UpperSignatureVerifier;
import com.cloud.gateway.domain.security.signature.LegacySecretTimestampBodySha256SignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureCryptography;
import com.cloud.gateway.domain.security.signature.SecretTimestampBodyHmacSha256SignatureVerifier;
import org.junit.jupiter.api.Test;

class RequestSignatureVerifierTest {
    private static final String SECRET = "secret";
    private static final String TIMESTAMP = "1777777777000";
    private static final byte[] PAYLOAD = "payload".getBytes(StandardCharsets.UTF_8);

    @Test
    void shouldPreserveIndependentHistoricalVectorsAndCaseCompatibility() {
        assertTrue(new LegacySecretParamsTimestampMd5UpperSignatureVerifier(new RequestSignatureCryptography()).verify(SECRET,
                evidence("650590469E844FED040E11F14569DA5D")));
        assertTrue(new LegacySecretTimestampBodySha256SignatureVerifier(new RequestSignatureCryptography()).verify(SECRET,
                evidence("5a131638338469d019211f62af27d5043ae358e74643ba3dc9ac0c3319293d10")));
        assertTrue(new SecretTimestampBodyHmacSha256SignatureVerifier(new RequestSignatureCryptography()).verify(SECRET,
                evidence("A6D0A7956DB591BAAF95FDF6830F5AFD7EC2B36082226E43A022224288C28C9B")));
        assertFalse(new SecretTimestampBodyHmacSha256SignatureVerifier(new RequestSignatureCryptography()).verify("wrong-secret",
                evidence("a6d0a7956db591baaf95fdf6830f5afd7ec2b36082226e43a022224288c28c9b")));
    }

    @Test
    void shouldVerifyLegacySecretParamsTimestampMd5UpperContract() throws Exception {
        String signature = digest(RequestSignatureCryptography.MD5,
                SECRET + "payload" + TIMESTAMP).toUpperCase();

        assertTrue(new LegacySecretParamsTimestampMd5UpperSignatureVerifier(new RequestSignatureCryptography())
                .verify(SECRET, evidence(signature)));
    }

    @Test
    void shouldVerifyLegacySecretTimestampBodySha256Contract() throws Exception {
        String signature = digest(RequestSignatureCryptography.SHA_256,
                SECRET + TIMESTAMP + "payload");

        assertTrue(new LegacySecretTimestampBodySha256SignatureVerifier(new RequestSignatureCryptography())
                .verify(SECRET, evidence(signature)));
    }

    @Test
    void shouldVerifySecretTimestampBodyHmacSha256Contract() throws Exception {
        Mac mac = Mac.getInstance(RequestSignatureCryptography.HMAC_SHA_256);
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),
                RequestSignatureCryptography.HMAC_SHA_256));
        mac.update(TIMESTAMP.getBytes(StandardCharsets.UTF_8));
        String signature = HexFormat.of().formatHex(mac.doFinal(PAYLOAD));

        assertTrue(new SecretTimestampBodyHmacSha256SignatureVerifier(new RequestSignatureCryptography())
                .verify(SECRET, evidence(signature)));
    }

    private static ClientSignatureEvidence evidence(String signature) {
        return new ClientSignatureEvidence(PAYLOAD, TIMESTAMP, signature);
    }

    private static String digest(String algorithm, String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm)
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}

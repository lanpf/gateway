package com.cloud.gateway.infrastructure.security.spring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.application.security.ChannelSignatureEvidence;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.cloud.gateway.infrastructure.security.spring.signature.LegacySecretParamsTimestampMd5UpperSignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.LegacySecretTimestampBodySha256SignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureCryptography;
import com.cloud.gateway.infrastructure.security.spring.signature.SecretTimestampBodyHmacSha256SignatureVerifier;
import org.junit.jupiter.api.Test;

class RequestSignatureVerifierTest {
    private static final String SECRET = "secret";
    private static final String TIMESTAMP = "1777777777000";
    private static final byte[] PAYLOAD = "payload".getBytes(StandardCharsets.UTF_8);

    @Test
    void shouldVerifyLegacySecretParamsTimestampMd5UpperContract() throws Exception {
        String signature = digest(RequestSignatureCryptography.MD5,
                SECRET + "payload" + TIMESTAMP).toUpperCase();

        assertTrue(new LegacySecretParamsTimestampMd5UpperSignatureVerifier()
                .verify(SECRET, evidence(signature)));
    }

    @Test
    void shouldVerifyLegacySecretTimestampBodySha256Contract() throws Exception {
        String signature = digest(RequestSignatureCryptography.SHA_256,
                SECRET + TIMESTAMP + "payload");

        assertTrue(new LegacySecretTimestampBodySha256SignatureVerifier()
                .verify(SECRET, evidence(signature)));
    }

    @Test
    void shouldVerifySecretTimestampBodyHmacSha256Contract() throws Exception {
        Mac mac = Mac.getInstance(RequestSignatureCryptography.HMAC_SHA_256);
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),
                RequestSignatureCryptography.HMAC_SHA_256));
        mac.update(TIMESTAMP.getBytes(StandardCharsets.UTF_8));
        String signature = HexFormat.of().formatHex(mac.doFinal(PAYLOAD));

        assertTrue(new SecretTimestampBodyHmacSha256SignatureVerifier()
                .verify(SECRET, evidence(signature)));
    }

    private static ChannelSignatureEvidence evidence(String signature) {
        return new ChannelSignatureEvidence(TIMESTAMP, PAYLOAD, signature);
    }

    private static String digest(String algorithm, String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm)
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}

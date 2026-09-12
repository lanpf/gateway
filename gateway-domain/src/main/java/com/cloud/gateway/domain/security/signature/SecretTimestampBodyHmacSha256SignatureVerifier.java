package com.cloud.gateway.domain.security.signature;

import com.cloud.gateway.domain.client.SignatureContract;

@lombok.RequiredArgsConstructor
public final class SecretTimestampBodyHmacSha256SignatureVerifier implements RequestSignatureVerifier {
    private final SignatureCryptography cryptography;
    @Override
    public SignatureContract contract() {
        return SignatureContract.LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256;
    }

    @Override
    public boolean verify(String secret, SignatureInput evidence) {
        String expected = cryptography.hmacSha256(secret, signingPayload(evidence));
        return SignatureHex.matchesLegacy(cryptography, expected, evidence.signature());
    }
    private static byte[] signingPayload(SignatureInput evidence) {
        byte[] timestamp = evidence.timestamp().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] body = evidence.payload();
        byte[] payload = java.util.Arrays.copyOf(timestamp, timestamp.length + body.length);
        System.arraycopy(body, 0, payload, timestamp.length, body.length);
        return payload;
    }
}

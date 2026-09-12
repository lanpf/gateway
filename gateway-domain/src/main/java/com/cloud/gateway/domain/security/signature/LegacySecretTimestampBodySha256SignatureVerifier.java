package com.cloud.gateway.domain.security.signature;

import com.cloud.gateway.domain.client.SignatureContract;

import java.nio.charset.StandardCharsets;

@lombok.RequiredArgsConstructor
public final class LegacySecretTimestampBodySha256SignatureVerifier implements RequestSignatureVerifier {
    private final SignatureCryptography cryptography;
    @Override
    public SignatureContract contract() {
        return SignatureContract.LEGACY_SECRET_TIMESTAMP_BODY_SHA256;
    }

    @Override
    public boolean verify(String secret, SignatureInput evidence) {
        String expected = cryptography.digest(SignatureCryptography.Digest.SHA_256,
                (secret + evidence.timestamp() + new String(evidence.payload(), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        return SignatureHex.matchesLegacy(cryptography, expected, evidence.signature());
    }
}

package com.cloud.gateway.domain.security.signature;

import com.cloud.gateway.domain.client.SignatureContract;

import java.nio.charset.StandardCharsets;

@lombok.RequiredArgsConstructor
public final class LegacySecretParamsTimestampMd5UpperSignatureVerifier implements RequestSignatureVerifier {
    private final SignatureCryptography cryptography;
    @Override
    public SignatureContract contract() {
        return SignatureContract.LEGACY_SECRET_PARAMS_TIMESTAMP_MD5_UPPER;
    }

    @Override
    public boolean verify(String secret, SignatureInput evidence) {
        String expected = cryptography.digest(SignatureCryptography.Digest.MD5,
                (secret + new String(evidence.payload(), StandardCharsets.UTF_8) + evidence.timestamp()).getBytes(StandardCharsets.UTF_8)).toUpperCase(java.util.Locale.ROOT);
        return SignatureHex.matchesLegacy(cryptography, expected, evidence.signature());
    }
}

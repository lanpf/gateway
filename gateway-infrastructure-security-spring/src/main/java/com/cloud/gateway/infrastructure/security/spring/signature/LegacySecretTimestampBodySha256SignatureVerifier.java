package com.cloud.gateway.infrastructure.security.spring.signature;

import com.cloud.gateway.application.security.ChannelSignatureEvidence;
import com.cloud.gateway.domain.policy.SignaturePayloadContract;

import java.nio.charset.StandardCharsets;

public final class LegacySecretTimestampBodySha256SignatureVerifier implements RequestSignatureVerifier {
    @Override
    public SignaturePayloadContract contract() {
        return SignaturePayloadContract.LEGACY_SECRET_TIMESTAMP_BODY_SHA256;
    }

    @Override
    public boolean verify(String secret, ChannelSignatureEvidence evidence) {
        String expected = RequestSignatureCryptography.digest(RequestSignatureCryptography.SHA_256,
                secret + evidence.timestamp() + new String(evidence.payload(), StandardCharsets.UTF_8));
        return RequestSignatureCryptography.constantTimeEquals(expected, evidence.signature());
    }
}

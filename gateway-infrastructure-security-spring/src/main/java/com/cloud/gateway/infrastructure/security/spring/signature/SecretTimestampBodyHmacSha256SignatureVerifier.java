package com.cloud.gateway.infrastructure.security.spring.signature;

import com.cloud.gateway.application.security.ChannelSignatureEvidence;
import com.cloud.gateway.domain.policy.SignaturePayloadContract;

public final class SecretTimestampBodyHmacSha256SignatureVerifier implements RequestSignatureVerifier {
    @Override
    public SignaturePayloadContract contract() {
        return SignaturePayloadContract.SECRET_TIMESTAMP_BODY_HMAC_SHA256;
    }

    @Override
    public boolean verify(String secret, ChannelSignatureEvidence evidence) {
        String expected = RequestSignatureCryptography.hmacSha256(
                secret, evidence.timestamp(), evidence.payload());
        return RequestSignatureCryptography.constantTimeEquals(expected, evidence.signature());
    }
}

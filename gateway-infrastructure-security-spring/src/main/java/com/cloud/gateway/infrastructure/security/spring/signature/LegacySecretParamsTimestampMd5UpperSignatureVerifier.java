package com.cloud.gateway.infrastructure.security.spring.signature;

import com.cloud.gateway.application.security.ChannelSignatureEvidence;
import com.cloud.gateway.domain.policy.SignaturePayloadContract;

import java.nio.charset.StandardCharsets;

public final class LegacySecretParamsTimestampMd5UpperSignatureVerifier implements RequestSignatureVerifier {
    @Override
    public SignaturePayloadContract contract() {
        return SignaturePayloadContract.LEGACY_SECRET_PARAMS_TIMESTAMP_MD5_UPPER;
    }

    @Override
    public boolean verify(String secret, ChannelSignatureEvidence evidence) {
        String expected = RequestSignatureCryptography.digest(RequestSignatureCryptography.MD5,
                secret + new String(evidence.payload(), StandardCharsets.UTF_8) + evidence.timestamp()).toUpperCase();
        return RequestSignatureCryptography.constantTimeEquals(expected, evidence.signature());
    }
}

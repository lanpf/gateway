package com.cloud.gateway.application.security;

import org.apache.commons.lang3.Validate;
import com.cloud.gateway.domain.security.signature.SignatureInput;

/** Original, unmodified request evidence. Optional fields are required by the v1 verifier only. */
public record ClientSignatureEvidence(
        byte[] payload, String timestamp, String signature,
        String method, String rawPath, String rawQuery, String appId, String clientId,
        String credentialId, String nonce) implements SignatureInput {
    public ClientSignatureEvidence {
        payload = payload == null ? new byte[0] : payload.clone();
        Validate.notBlank(timestamp, "timestamp must not be blank");
        Validate.notBlank(signature, "signature must not be blank");
    }

    public ClientSignatureEvidence(byte[] payload, String timestamp, String signature) {
        this(payload, timestamp, signature, null, null, null, null, null, null, null);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public String toString() {
        return "ClientSignatureEvidence[redacted]";
    }
}

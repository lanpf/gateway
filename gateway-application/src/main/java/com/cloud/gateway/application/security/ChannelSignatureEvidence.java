package com.cloud.gateway.application.security;

import org.apache.commons.lang3.Validate;

public record ChannelSignatureEvidence(byte[] payload, String timestamp, String signature) {
    public ChannelSignatureEvidence {
        payload = payload == null ? new byte[0] : payload.clone();
        Validate.notBlank(timestamp, "timestamp must not be blank");
        Validate.notBlank(signature, "signature must not be blank");
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}

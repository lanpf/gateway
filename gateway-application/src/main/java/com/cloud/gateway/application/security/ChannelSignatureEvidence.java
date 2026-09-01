package com.cloud.gateway.application.security;

import org.apache.commons.lang3.Validate;

public record ChannelSignatureEvidence(String timestamp, byte[] payload, String signature) {
    public ChannelSignatureEvidence {
        Validate.notBlank(timestamp, "timestamp must not be blank");
        Validate.notBlank(signature, "signature must not be blank");
        payload = payload == null ? new byte[0] : payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}

package com.cloud.gateway.infrastructure.security.spring.signature;

import com.cloud.gateway.application.security.ChannelSignatureEvidence;
import com.cloud.gateway.domain.policy.SignaturePayloadContract;

public interface RequestSignatureVerifier {
    SignaturePayloadContract contract();

    boolean verify(String secret, ChannelSignatureEvidence evidence);
}

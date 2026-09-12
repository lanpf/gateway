package com.cloud.gateway.domain.security.signature;

import com.cloud.gateway.domain.client.SignatureContract;

public interface RequestSignatureVerifier {
    SignatureContract contract();

    boolean verify(String secret, SignatureInput evidence);
}

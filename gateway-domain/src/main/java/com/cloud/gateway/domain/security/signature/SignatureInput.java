package com.cloud.gateway.domain.security.signature;

/** Original request facts, independent of HTTP bindings and application carriers. */
public interface SignatureInput {
    byte[] payload();
    String timestamp();
    String signature();
    String method();
    String rawPath();
    String rawQuery();
    String appId();
    String clientId();
    String credentialId();
    String nonce();
}

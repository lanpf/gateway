package com.cloud.gateway.domain.security.signature;

/** Cryptographic operations required by the signature protocols; hex output is lowercase. */
public interface SignatureCryptography {
    enum Digest { MD5, SHA_256 }

    String digest(Digest algorithm, byte[] input);
    String hmacSha256(String secret, byte[] input);
    boolean matches(String expected, String actual);
}

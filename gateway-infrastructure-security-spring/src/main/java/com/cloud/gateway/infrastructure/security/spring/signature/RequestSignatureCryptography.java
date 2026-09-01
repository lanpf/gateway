package com.cloud.gateway.infrastructure.security.spring.signature;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RequestSignatureCryptography {
    public static final String MD5 = "MD5";
    public static final String SHA_256 = "SHA-256";
    public static final String HMAC_SHA_256 = "HmacSHA256";

    private RequestSignatureCryptography() { }

    static String digest(String algorithm, String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm)
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("algorithm not available: " + algorithm, exception);
        }
    }

    static String hmacSha256(String secret, String timestamp, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("algorithm not available: " + HMAC_SHA_256, exception);
        }
    }

    static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
}

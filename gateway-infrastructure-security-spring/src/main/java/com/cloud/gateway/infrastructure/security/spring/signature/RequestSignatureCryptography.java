package com.cloud.gateway.infrastructure.security.spring.signature;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.cloud.gateway.domain.security.signature.SignatureCryptography;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RequestSignatureCryptography implements SignatureCryptography {
    public static final String MD5 = "MD5";
    public static final String SHA_256 = "SHA-256";
    public static final String HMAC_SHA_256 = "HmacSHA256";

    public RequestSignatureCryptography() { }

    @Override
    public String digest(Digest algorithm, byte[] input) {
        return digest(switch (algorithm) {
            case MD5 -> MD5;
            case SHA_256 -> SHA_256;
        }, input);
    }

    @Override
    public String hmacSha256(String secret, byte[] input) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return HexFormat.of().formatHex(mac.doFinal(input));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("algorithm not available: " + HMAC_SHA_256, exception);
        }
    }

    @Override
    public boolean matches(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    static String digest(String algorithm, String value) {
        return digest(algorithm, value.getBytes(StandardCharsets.UTF_8));
    }

    static String digest(String algorithm, byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm)
                    .digest(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("algorithm not available: " + algorithm, exception);
        }
    }

}

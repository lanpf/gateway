package com.cloud.gateway.domain.security.signature;

import com.cloud.gateway.domain.client.SignatureContract;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * V1 wire contract: HMAC-SHA256 over UTF-8 netstrings (decimal UTF-8 byte length,
 * colon, value, comma), in this exact order: protocol name, method, raw path,
 * raw query, appId, clientId, credentialId, timestamp, nonce, lowercase hex
 * SHA-256 of the original body bytes. No decoding, sorting, trimming, case or
 * Unicode normalization is performed. Absent query is encoded as -1:, whereas
 * an explicitly empty query is 0:,. Timestamp is canonical epoch milliseconds.
 * Signature is exactly 64 lowercase hexadecimal characters. Nonce is 22-128
 * base64url characters (without padding), generated with at least 128 random bits.
 */
@RequiredArgsConstructor
public final class HttpRequestSignatureV1Verifier implements RequestSignatureVerifier {
    private final SignatureCryptography cryptography;
    private static final Pattern METHOD = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,32}");
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9_-]{22,128}");
    private static final Pattern TIMESTAMP = Pattern.compile("0|[1-9][0-9]{0,18}");
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");
    private static final String ABSENT_FIELD = "-1:,";
    private static final int MAX_ID_LENGTH = 256;

    @Override
    public SignatureContract contract() {
        return SignatureContract.HTTP_REQUEST_SIGNATURE_V1;
    }

    @Override
    public boolean verify(String secret, SignatureInput evidence) {
        if (!valid(evidence)) {
            return false;
        }
        String expected = cryptography.hmacSha256(secret, canonicalPayload(evidence,
                cryptography.digest(SignatureCryptography.Digest.SHA_256, evidence.payload())));
        return cryptography.matches(expected, evidence.signature());
    }

    /** Public for interoperable signing clients and fixed-vector protocol tests. */
    public static byte[] canonicalPayload(SignatureInput evidence, String bodyDigest) {
        StringBuilder payload = new StringBuilder();
        append(payload, SignatureContract.HTTP_REQUEST_SIGNATURE_V1.name());
        append(payload, evidence.method());
        append(payload, evidence.rawPath());
        append(payload, evidence.rawQuery());
        append(payload, evidence.appId());
        append(payload, evidence.clientId());
        append(payload, evidence.credentialId());
        append(payload, evidence.timestamp());
        append(payload, evidence.nonce());
        append(payload, bodyDigest);
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void append(StringBuilder output, String value) {
        if (value == null) {
            output.append(ABSENT_FIELD);
        } else {
            output.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value).append(',');
        }
    }

    public static boolean valid(SignatureInput evidence) {
        return evidence.method() != null && METHOD.matcher(evidence.method()).matches()
                && validRawTarget(evidence.rawPath(), true)
                && (evidence.rawQuery() == null || validRawTarget(evidence.rawQuery(), false))
                && validId(evidence.appId()) && validId(evidence.clientId()) && validId(evidence.credentialId())
                && evidence.timestamp() != null && TIMESTAMP.matcher(evidence.timestamp()).matches()
                && evidence.nonce() != null && NONCE.matcher(evidence.nonce()).matches()
                && evidence.signature() != null && SIGNATURE.matcher(evidence.signature()).matches();
    }

    private static boolean validId(String value) {
        return StringUtils.isNotBlank(value) && value.length() <= MAX_ID_LENGTH
                && value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e);
    }

    private static boolean validRawTarget(String value, boolean path) {
        if (value == null || (path && !value.startsWith("/"))) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e || character == '#' || (path && character == '?')) {
                return false;
            }
            if (character == '%') {
                if (index + 2 >= value.length() || Character.digit(value.charAt(index + 1), 16) < 0
                        || Character.digit(value.charAt(index + 2), 16) < 0) {
                    return false;
                }
                index += 2;
            }
        }
        return true;
    }
}

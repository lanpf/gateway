package com.cloud.gateway.domain.security.signature;

import java.util.Locale;

/** Historical case compatibility is a protocol rule, not a property of constant-time comparison. */
final class SignatureHex {
    private SignatureHex() { }

    static boolean matchesLegacy(SignatureCryptography cryptography, String expected, String actual) {
        return actual != null && cryptography.matches(expected.toLowerCase(Locale.ROOT), actual.toLowerCase(Locale.ROOT));
    }
}

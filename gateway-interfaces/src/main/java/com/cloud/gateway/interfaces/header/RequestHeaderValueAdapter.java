package com.cloud.gateway.interfaces.header;

import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Shared declaration sanitization for HTTP binding and downstream forwarding, never authentication. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequestHeaderValueAdapter {
    private static final int MAX_APP_VERSION_LENGTH = 64;
    private static final Pattern APP_VERSION = Pattern.compile("[A-Za-z0-9._+-]+");

    public static Optional<String> sanitizedAppVersion(String value) {
        if (value == null || value.length() > MAX_APP_VERSION_LENGTH || !APP_VERSION.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}

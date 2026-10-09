package com.cloud.gateway.domain.capability.header;

import com.cloud.framework.core.RequestHeader;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Shared declaration sanitization for HTTP binding and downstream forwarding, never authentication. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeclaredHeaderResolver {
    private static final int MAX_APP_VERSION_LENGTH = 64;
    private static final Pattern APP_VERSION = Pattern.compile("[A-Za-z0-9._+-]+");

    public static boolean supports(RequestHeader header) {
        return header != null && supportedHeaders().contains(header);
    }

    public static Set<RequestHeader> supportedHeaders() {
        return Set.of(RequestHeader.APP_VERSION);
    }

    public static Optional<String> resolve(RequestHeader header, String value) {
        if (!supports(header)) {
            return Optional.empty();
        }
        if (value == null || value.length() > MAX_APP_VERSION_LENGTH || !APP_VERSION.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}

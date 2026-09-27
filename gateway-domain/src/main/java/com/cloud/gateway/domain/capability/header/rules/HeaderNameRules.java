package com.cloud.gateway.domain.capability.header.rules;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;

/** Common format boundary for any header name: HTTP token characters, length cap, lowercase form. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HeaderNameRules {
    public static final int MAX_NAME_LENGTH = 128;
    private static final String NAME_TOKEN = "[!#$%&'*+.^_`|~0-9A-Za-z-]+";

    /** Validates the name and returns its trimmed lowercase form. */
    public static String normalize(String name) {
        return Require.that(name,
                v -> v.length() <= MAX_NAME_LENGTH && v.matches(NAME_TOKEN)
        , DomainException::invalidObjectField).trim().toLowerCase(Locale.ROOT);
    }
}

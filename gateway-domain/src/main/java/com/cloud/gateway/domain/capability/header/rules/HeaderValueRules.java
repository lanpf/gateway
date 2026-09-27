package com.cloud.gateway.domain.capability.header.rules;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Common value boundary for configured and trusted outgoing Header values; empty values are valid. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HeaderValueRules {
    public static final int MAX_VALUE_LENGTH = 4096;

    public static void validate(String value) {
            Require.that(value,
                v -> v.length() <= MAX_VALUE_LENGTH && v.codePoints().noneMatch(Character::isISOControl)
        , DomainException::invalidObjectField);
    }
}

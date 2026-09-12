package com.cloud.gateway.domain.policy.header;

import com.cloud.gateway.domain.common.DomainException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Common value boundary for configured and trusted outgoing Header values; empty values are valid. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HeaderValueRules {
    public static final int MAX_VALUE_LENGTH = 4096;

    public static void requireValid(String value) {
        if (value == null || value.length() > MAX_VALUE_LENGTH
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw DomainException.invalidField();
        }
    }
}

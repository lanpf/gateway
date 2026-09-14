package com.cloud.gateway.domain.policy.capability;

import com.cloud.gateway.domain.common.StringEntityId;

/** Stable rate-limit rule identity; survives snapshot and quota parameter updates. */
public final class RateLimitRuleId extends StringEntityId {
    public RateLimitRuleId(String value) {
        super(value);
    }
}

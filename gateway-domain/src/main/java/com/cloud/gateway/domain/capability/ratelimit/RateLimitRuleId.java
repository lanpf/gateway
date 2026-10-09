package com.cloud.gateway.domain.capability.ratelimit;

import com.cloud.gateway.domain.common.AbstractStringEntityId;

/** Stable rate-limit rule identity; survives snapshot and quota parameter updates. */
public final class RateLimitRuleId extends AbstractStringEntityId {
    public RateLimitRuleId(String value) {
        super(value);
    }
}

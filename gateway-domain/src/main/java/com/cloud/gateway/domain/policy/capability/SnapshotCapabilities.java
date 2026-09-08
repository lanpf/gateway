package com.cloud.gateway.domain.policy.capability;

import java.util.List;

/** 策略快照的能力节（schemaVersion 2）；缺省 = 全部能力关闭。 */
public record SnapshotCapabilities(
        RateLimitRules rateLimit,
        List<CircuitBreakerPolicy> circuitBreaker,
        List<TimeoutPolicy> timeout,
        List<CachePolicy> cache
) {
    public SnapshotCapabilities {
        rateLimit = rateLimit == null ? RateLimitRules.disabled() : rateLimit;
        circuitBreaker = circuitBreaker == null ? List.of() : List.copyOf(circuitBreaker);
        timeout = timeout == null ? List.of() : List.copyOf(timeout);
        cache = cache == null ? List.of() : List.copyOf(cache);
    }

    public static SnapshotCapabilities disabled() {
        return new SnapshotCapabilities(RateLimitRules.disabled(), List.of(), List.of(), List.of());
    }
}

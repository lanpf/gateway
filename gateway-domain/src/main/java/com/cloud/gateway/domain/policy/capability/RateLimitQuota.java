package com.cloud.gateway.domain.policy.capability;

/**
 * 限流配额端口：领域只声明"按规则取一次配额"，由基础设施以 Redis Lua
 * （EXACT）或本地预分配池（PREALLOCATED）实现。
 */
public interface RateLimitQuota {

    RateLimitDecision tryAcquire(RateLimitPolicy rule, String key);
}

package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitConfig;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;

/** Structural wire projections and explicit invariant-preserving reconstruction. */
public interface CapabilityNodeStorageMapper {
    StoredCapabilityPolicyConfig.RateLimit toStored(RateLimitConfig source);
    StoredCapabilityPolicyConfig.Timeout toStored(TimeoutConfig source);
    StoredCapabilityPolicyConfig.Headers toStored(HeaderConfig source);
    RateLimitConfig toDomain(StoredCapabilityPolicyConfig.RateLimit source);
    TimeoutConfig toDomain(StoredCapabilityPolicyConfig.Timeout source);
    HeaderConfig toDomain(StoredCapabilityPolicyConfig.Headers source);
}

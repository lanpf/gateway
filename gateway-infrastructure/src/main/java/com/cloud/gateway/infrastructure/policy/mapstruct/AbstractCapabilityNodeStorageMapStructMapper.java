package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.framework.core.mapper.MapStructConfig;
import com.cloud.gateway.domain.capability.header.*;
import com.cloud.gateway.domain.capability.ratelimit.*;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import com.cloud.gateway.infrastructure.policy.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public abstract class AbstractCapabilityNodeStorageMapStructMapper implements CapabilityNodeStorageMapper {
    @Override @Mapping(target = "onRedisFailure", source = "failureMode")
    public abstract StoredCapabilityPolicyConfig.RateLimit toStored(RateLimitConfig source);
    @Override public abstract StoredCapabilityPolicyConfig.Timeout toStored(TimeoutConfig source);
    @Override public abstract StoredCapabilityPolicyConfig.Headers toStored(HeaderConfig source);
    protected String ruleId(RateLimitRuleId id) { return id.value(); }

    @Override public RateLimitConfig toDomain(StoredCapabilityPolicyConfig.RateLimit source) {
        return new RateLimitConfig(source.rules().stream().map(rule -> new RateLimitRule(
                rule.ruleId(), rule.dimension(), rule.capacity(), rule.refillPerSecond(), rule.mode())).toList(),
                source.onRedisFailure());
    }
    @Override public TimeoutConfig toDomain(StoredCapabilityPolicyConfig.Timeout source) {
        return new TimeoutConfig(source.connect(), source.read(), source.total());
    }
    @Override public HeaderConfig toDomain(StoredCapabilityPolicyConfig.Headers source) {
        StoredCapabilityPolicyConfig.Forwarding forwarding = source.credentialForwarding();
        return new HeaderConfig(source.trustedHeaders(), source.setHeaders(), source.removeHeaders(),
                forwarding == null ? CredentialForwarding.stripAll() : new CredentialForwarding(
                        forwarding.authorizationHeader(), forwarding.cookies(), forwarding.allowedCookieNames()));
    }
}

package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.gateway.domain.common.StringEntityId;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import com.cloud.gateway.infrastructure.policy.*;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;

@Mapper(config = PolicyStorageMapperConfig.class)
public abstract class PolicyConfigStorageMapStructMapper implements PolicyConfigStorageMapper {
    @Override public abstract StoredPolicyConfig toStored(PolicyConfig source);
    protected abstract StoredPolicyConfig.Bundle bundle(PolicyBundle source);
    protected abstract StoredPolicyConfig.Binding binding(PolicyBinding source);
    protected String identifier(StringEntityId source) { return source == null ? null : source.value(); }

    /** Explicit domain construction runs invariants; storage objects never become runtime policies directly. */
    @Override public PolicyConfig toDomain(StoredPolicyConfig source) {
        java.util.Objects.requireNonNull(source, "Policy configuration is required");
        return new PolicyConfig(source.bundles().entrySet().stream().collect(Collectors.toMap(
                entry -> new PolicyBundleId(entry.getKey()), entry -> domainBundle(entry.getValue()))),
                source.bindings().stream().map(value -> new PolicyBinding(
                        new PolicyBindingId(value.bindingId()), new PolicyBundleId(value.bundleId()),
                        value.serviceId(), value.endpointId() == null ? null : new EndpointId(value.endpointId()),
                        value.tags(), value.appId() == null ? null : new AppId(value.appId()),
                        value.verifiedClientId() == null ? null : new ClientId(value.verifiedClientId()),
                        value.subjectType())).toList());
    }

    private PolicyBundle domainBundle(StoredPolicyConfig.Bundle value) {
        StoredPolicyConfig.RateLimit rate = value.rateLimit();
        StoredPolicyConfig.Timeout timeout = value.timeout();
        StoredPolicyConfig.CircuitBreaker breaker = value.circuitBreaker();
        StoredPolicyConfig.Cache cache = value.cache();
        StoredPolicyConfig.Headers headers = value.headers();
        return new PolicyBundle(new PolicyBundleId(value.bundleId()),
                rate == null ? null : new RateLimitRules(rate.rules().stream().map(rule -> new RateLimitRule(
                        rule.ruleId(), rule.dimension(), rule.capacity(), rule.refillPerSecond(), rule.mode())).toList(), rate.onRedisFailure()),
                timeout == null ? null : new TimeoutPolicy(timeout.connect(), timeout.read(), timeout.total()),
                breaker == null ? null : new CircuitBreakerPolicy(breaker.failureRateThreshold(), breaker.slowCallRateThreshold(),
                        breaker.slowCallDuration(), breaker.minimumCalls(), breaker.slidingWindowSize(), breaker.halfOpenCalls(),
                        breaker.waitDuration(), breaker.fallback()),
                cache == null ? null : new CachePolicy(cache.ttl(), cache.staleWindow(), cache.maxBodyBytes(), cache.varyHeaders()),
                headers == null ? null : new HeaderPolicy(headers.trustedHeaders(), headers.setHeaders(), headers.removeHeaders(),
                        headers.credentialForwarding() == null ? CredentialForwarding.stripAll()
                                : new CredentialForwarding(headers.credentialForwarding().authorizationHeader(),
                                        headers.credentialForwarding().cookies(), headers.credentialForwarding().allowedCookieNames())));
    }
}

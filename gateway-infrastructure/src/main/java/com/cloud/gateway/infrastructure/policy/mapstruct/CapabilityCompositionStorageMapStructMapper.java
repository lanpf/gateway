package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.gateway.domain.common.StringEntityId;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.ServiceId;
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
public abstract class CapabilityCompositionStorageMapStructMapper implements CapabilityCompositionStorageMapper {
    @Override public abstract StoredCapabilityComposition toStored(CapabilityComposition source);
    protected abstract StoredCapabilityComposition.Bundle bundle(CapabilityBundle source);
    protected abstract StoredCapabilityComposition.Binding binding(CapabilityBinding source);
    protected String identifier(StringEntityId source) { return source == null ? null : source.value(); }
    protected String serviceIdentifier(com.cloud.gateway.domain.endpoint.ServiceId source) {
        return source == null ? null : source.value();
    }

    /** Explicit domain construction runs invariants; storage objects never become runtime capabilityComposition directly. */
    @Override public CapabilityComposition toDomain(StoredCapabilityComposition source) {
        java.util.Objects.requireNonNull(source, "Policy configuration is required");
        return new CapabilityComposition(source.bundles().entrySet().stream().collect(Collectors.toMap(
                entry -> new CapabilityBundleId(entry.getKey()), entry -> domainBundle(entry.getValue()))),
                source.bindings().stream().map(value -> new CapabilityBinding(
                        new CapabilityBindingId(value.bindingId()), new CapabilityBundleId(value.bundleId()),
                        value.serviceId() == null ? null : new ServiceId(value.serviceId()),
                        value.endpointId() == null ? null : new EndpointId(value.endpointId()),
                        value.tags(), value.appId() == null ? null : new AppId(value.appId()),
                        value.verifiedClientId() == null ? null : new ClientId(value.verifiedClientId()),
                        value.subjectType())).toList());
    }

    private CapabilityBundle domainBundle(StoredCapabilityComposition.Bundle value) {
        StoredCapabilityComposition.RateLimit rate = value.rateLimit();
        StoredCapabilityComposition.Timeout timeout = value.timeout();
        StoredCapabilityComposition.CircuitBreaker breaker = value.circuitBreaker();
        StoredCapabilityComposition.Cache cache = value.cache();
        StoredCapabilityComposition.Headers headers = value.headers();
        return new CapabilityBundle(new CapabilityBundleId(value.bundleId()),
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

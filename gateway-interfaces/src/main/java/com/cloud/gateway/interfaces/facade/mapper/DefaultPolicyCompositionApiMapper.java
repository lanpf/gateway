package com.cloud.gateway.interfaces.facade.mapper;


import com.cloud.gateway.api.policy.*;
import com.cloud.gateway.application.policy.SavePolicyCompositionCommand;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.policy.header.*;
import java.util.stream.Collectors;

/** Semantic construction deliberately invokes domain invariants instead of structural copying. */
public final class DefaultPolicyCompositionApiMapper implements PolicyCompositionApiMapper {
    @Override public SavePolicyCompositionCommand toCommand(SavePolicyCompositionApiCommand source) {
        PolicyCompositionApiCommand config = source.policies();
        return new SavePolicyCompositionCommand(source.expectedSourceRevision(), new PolicyComposition(
                config.bundles().entrySet().stream().collect(Collectors.toMap(
                        entry -> new PolicyBundleId(entry.getKey()), entry -> bundle(entry.getValue()))),
                config.bindings().stream().map(value -> new PolicyBinding(
                        new PolicyBindingId(value.bindingId()), new PolicyBundleId(value.bundleId()),
                        value.serviceId(), value.endpointId() == null ? null : new EndpointId(value.endpointId()),
                        value.tags(), value.appId() == null ? null : new AppId(value.appId()),
                        value.verifiedClientId() == null ? null : new ClientId(value.verifiedClientId()),
                        value.subjectType() == null ? null : SubjectType.valueOf(value.subjectType().name()))).toList()));
    }

    private PolicyBundle bundle(PolicyCompositionApiCommand.Bundle value) {
        PolicyCompositionApiCommand.RateLimit rate = value.rateLimit();
        PolicyCompositionApiCommand.Timeout timeout = value.timeout();
        PolicyCompositionApiCommand.CircuitBreaker breaker = value.circuitBreaker();
        PolicyCompositionApiCommand.Cache cache = value.cache();
        PolicyCompositionApiCommand.Headers headers = value.headers();
        return new PolicyBundle(new PolicyBundleId(value.bundleId()),
                rate == null ? null : new RateLimitRules(rate.rules().stream().map(rule -> new RateLimitPolicy(
                        rule.ruleId(), RateLimitDimension.valueOf(rule.dimension().name()), rule.capacity(),
                        rule.refillPerSecond(), RateLimitMode.valueOf(rule.mode().name()))).toList(),
                        RedisFailurePolicy.valueOf(rate.onRedisFailure().name())),
                timeout == null ? null : new TimeoutPolicy(timeout.connect(), timeout.read(), timeout.total()),
                breaker == null ? null : new CircuitBreakerPolicy(breaker.failureRateThreshold(), breaker.slowCallRateThreshold(),
                        breaker.slowCallDuration(), breaker.minimumCalls(), breaker.slidingWindowSize(), breaker.halfOpenCalls(),
                        breaker.waitDuration(), CircuitBreakerPolicy.Fallback.valueOf(breaker.fallback().name())),
                cache == null ? null : new CachePolicy(cache.ttl(), cache.staleWindow(), cache.maxBodyBytes(), cache.varyHeaders()),
                headers == null ? null : new HeaderPolicy(headers.trustedHeaders(),
                        headers.setHeaders(), headers.removeHeaders(),
                        new CredentialForwarding(
                                AuthorizationHeaderForwardingMode.valueOf(headers.credentialForwarding().authorizationHeader().name()),
                                CookieForwardingMode.valueOf(headers.credentialForwarding().cookies().name()),
                                headers.credentialForwarding().allowedCookieNames())));
    }
}

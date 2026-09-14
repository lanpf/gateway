package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.framework.core.RequestHeader;
import java.time.Duration;
import java.util.*;

/** Wire representation: identifiers are scalar strings and unordered collections are sorted. */
public record StoredPolicyConfig(Map<String, Bundle> bundles, List<Binding> bindings) {
    public StoredPolicyConfig {
        bundles = bundles == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(bundles));
        bindings = bindings == null ? List.of() : bindings.stream()
                .sorted(Comparator.comparing(Binding::bindingId)).toList();
    }

    public record Bundle(String bundleId, RateLimit rateLimit, Timeout timeout,
            CircuitBreaker circuitBreaker, Cache cache, Headers headers) { }

    public record Binding(String bindingId, String bundleId, String serviceId, String endpointId,
            Set<String> tags, String appId, String verifiedClientId, SubjectType subjectType) {
        public Binding { tags = sorted(tags); }
    }

    public record RateLimit(List<Rule> rules, RedisFailurePolicy onRedisFailure) {
        public RateLimit {
            rules = rules == null ? List.of() : rules.stream().sorted(Comparator.comparing(Rule::ruleId)).toList();
        }
    }
    public record Rule(String ruleId, RateLimitDimension dimension, int capacity,
            int refillPerSecond, RateLimitMode mode) { }
    public record Timeout(Duration connect, Duration read, Duration total) { }
    public record CircuitBreaker(int failureRateThreshold, int slowCallRateThreshold,
            Duration slowCallDuration, int minimumCalls, int slidingWindowSize,
            int halfOpenCalls, Duration waitDuration, CircuitBreakerPolicy.Fallback fallback) { }
    public record Cache(Duration ttl, Duration staleWindow, int maxBodyBytes, Set<String> varyHeaders) {
        public Cache { varyHeaders = sorted(varyHeaders); }
    }
    public record Headers(Set<RequestHeader> trustedHeaders, Map<String, String> setHeaders,
            Set<String> removeHeaders, StoredAccessSnapshot.Forwarding credentialForwarding) {
        public Headers {
            trustedHeaders = trustedHeaders == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(trustedHeaders));
            setHeaders = setHeaders == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(setHeaders));
            removeHeaders = sorted(removeHeaders);
        }
    }
    static Set<String> sorted(Set<String> values) {
        return values == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(values));
    }
}

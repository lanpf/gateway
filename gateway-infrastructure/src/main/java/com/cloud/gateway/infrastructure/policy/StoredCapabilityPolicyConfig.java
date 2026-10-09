package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.capability.header.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.capability.header.CookieForwardingMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RedisFailureMode;
import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.SubjectType;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import java.time.Duration;
import java.util.*;

/** Wire representation: identifiers are scalar strings and unordered collections are sorted. */
public record StoredCapabilityPolicyConfig(Map<String, Bundle> bundles, Map<String, Binding> bindings) {
    public StoredCapabilityPolicyConfig {
        bundles = bundles == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(NullSafe.copyOf(bundles)));
        bindings = bindings == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(NullSafe.copyOf(bindings)));
    }

    public record Bundle(String bundleId, Map<String, com.fasterxml.jackson.databind.JsonNode> capabilities) {
        public Bundle {
            Require.notNull(capabilities);
            Map<String, com.fasterxml.jackson.databind.JsonNode> copy = new TreeMap<>();
            NullSafe.forEach(capabilities, (id, node) -> copy.put(id, node.deepCopy()));
            capabilities = Collections.unmodifiableMap(copy);
        }
        @Override public Map<String, com.fasterxml.jackson.databind.JsonNode> capabilities() {
            Map<String, com.fasterxml.jackson.databind.JsonNode> copy = new TreeMap<>();
            capabilities.forEach((id, node) -> copy.put(id, node.deepCopy()));
            return Collections.unmodifiableMap(copy);
        }
    }

    public record Binding(String bindingId, String bundleId, String serviceId, String endpointId,
            Set<String> tags, String appId, String verifiedClientId, SubjectType subjectType) {
        public Binding { tags = sorted(tags); }
    }

    public record RateLimit(List<Rule> rules, RedisFailureMode onRedisFailure) {
        public RateLimit {
            rules = rules == null ? List.of() : NullSafe.copyOf(rules).stream().sorted(Comparator.comparing(Rule::ruleId)).toList();
        }
    }
    public record Rule(String ruleId, RateLimitDimension dimension, int capacity,
                       int refillPerSecond, RateLimitMode mode) { }
    public record Timeout(Duration connect, Duration read, Duration total) { }
    public record Headers(Set<RequestHeader> trustedHeaders, Map<String, String> setHeaders,
            Set<String> removeHeaders, Forwarding credentialForwarding) {
        public Headers {
            trustedHeaders = trustedHeaders == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(NullSafe.copyOf(trustedHeaders)));
            setHeaders = setHeaders == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(NullSafe.copyOf(setHeaders)));
            removeHeaders = sorted(removeHeaders);
        }
    }
    public record Forwarding(
            AuthorizationHeaderForwardingMode authorizationHeader,
            CookieForwardingMode cookies,
            Set<String> allowedCookieNames
    ) {
        public Forwarding {
            allowedCookieNames = sorted(allowedCookieNames);
        }
    }
    static Set<String> sorted(Set<String> values) {
        return values == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(NullSafe.copyOf(values)));
    }
}
